package com.example.FoodTourApp.service.impl;

import ai.onnxruntime.*;
import com.example.FoodTourApp.service.AntiSpoofService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AntiSpoofServiceImpl implements AntiSpoofService {

    private static final Logger log = LoggerFactory.getLogger(AntiSpoofServiceImpl.class);

    private static final int   INPUT_SIZE            = 224;
    private static final float STATIC_DIFF_THRESHOLD = 0.005f;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    private static final double MODEL_WEIGHT = 0.75;
    private static final double DIFF_WEIGHT  = 0.125;
    private static final double CV_WEIGHT    = 0.125;
    private static final double DIFF_CENTER  = 0.035;
    private static final double DIFF_SCALE   = 80.0;
    private static final double CV_CENTER    = 1.5;   // was 2.0 — real faces have cv ~1.5–2.0, fakes ~1.3–1.7
    private static final double CV_SCALE     = 3.0;

    // If model score is below this, aux signals (diff/cv) cannot compensate — treat as fake immediately.
    // Gap in data: fakes top out at ~0.16, weakest real face observed at ~0.18.
    private static final double MODEL_GATE   = 0.17;

    @Value("${face.model.antispoofing.path}")
    private String modelPath;

    @Value("${face.liveness.threshold}")
    private float livenessThreshold;

    private OrtEnvironment env;
    private OrtSession    session;
    private boolean       modelLoaded = false;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            opts.setInterOpNumThreads(1);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session     = env.createSession(modelPath, opts);
            modelLoaded = true;
            log.info("[AntiSpoof] Model loaded from {}  liveness_threshold={}", modelPath, livenessThreshold);
        } catch (Exception e) {
            log.warn("[AntiSpoof] Model not loaded: {}. Liveness check disabled.", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try { if (session != null) session.close(); } catch (OrtException ignored) {}
    }

    @Override
    public LivenessResult predict(String base64Frame, String base64FramePrev) throws Exception {
        if (!modelLoaded) return new LivenessResult(true, 1.0, false);

        byte[] frameBytes     = decodeBase64(base64Frame);
        byte[] framePrevBytes = (base64FramePrev != null && !base64FramePrev.isBlank())
                                ? decodeBase64(base64FramePrev) : null;

        Mat frame     = decodeMat(frameBytes);
        Mat framePrev = (framePrevBytes != null) ? decodeMat(framePrevBytes) : null;

        if (frame.empty()) throw new IllegalArgumentException("Could not decode current frame");

        boolean hasTemp = (framePrev != null && !framePrev.empty());

        float meanDiff = 0f, cv = 0f;
        if (hasTemp) {
            float[] metrics = computeDiffMetrics(frame, framePrev);
            meanDiff        = metrics[0];
            float stdDiff   = metrics[1];
            cv              = (meanDiff > 1e-6f) ? (stdDiff / meanDiff) : 0f;

            log.info("[AntiSpoof] mean_abs_diff={} std={} cv={}",
                     String.format("%.5f", meanDiff),
                     String.format("%.5f", stdDiff),
                     String.format("%.3f", cv));

            if (meanDiff < STATIC_DIFF_THRESHOLD) {
                log.info("[AntiSpoof] Identical frames detected (diff={}) → FAKE",
                         String.format("%.5f", meanDiff));
                frame.close(); framePrev.close();
                return new LivenessResult(false, 0.0, true);
            }
        }

        float[] input = build6ChannelInput(frame, framePrev);
        frame.close();
        if (framePrev != null) framePrev.close();

        return runInference(input, hasTemp, meanDiff, cv);
    }

    @Override
    public LivenessResult predict(String base64Frame) throws Exception {
        return predict(base64Frame, null);
    }

    private float[] computeDiffMetrics(Mat a, Mat b) {
        Mat sa = new Mat(), sb = new Mat(), fa = new Mat(), fb = new Mat();
        opencv_imgproc.resize(a, sa, new Size(64, 64));
        opencv_imgproc.resize(b, sb, new Size(64, 64));
        sa.convertTo(fa, opencv_core.CV_32FC3);
        sb.convertTo(fb, opencv_core.CV_32FC3);

        FloatIndexer ia = fa.createIndexer(), ib = fb.createIndexer();
        int count = 64 * 64 * 3;
        double sum = 0, sumSq = 0;
        for (int r = 0; r < 64; r++) {
            for (int c = 0; c < 64; c++) {
                for (int ch = 0; ch < 3; ch++) {
                    double d = Math.abs(ia.get(r, c, ch) - ib.get(r, c, ch)) / 255.0;
                    sum   += d;
                    sumSq += d * d;
                }
            }
        }
        ia.release(); ib.release();
        sa.close(); sb.close(); fa.close(); fb.close();

        double mean     = sum / count;
        double variance = sumSq / count - mean * mean;
        double std      = Math.sqrt(Math.max(0.0, variance));
        return new float[]{(float) mean, (float) std};
    }

    private float[] build6ChannelInput(Mat frame, Mat framePrev) {
        Mat f = resizeAndRgb(frame);
        int h = INPUT_SIZE, w = INPUT_SIZE, c = 3;
        float[] data = new float[6 * h * w];
        FloatIndexer fi = f.createIndexer();

        if (framePrev != null) {
            Mat fp = resizeAndRgb(framePrev);
            FloatIndexer fpi = fp.createIndexer();
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    for (int ch = 0; ch < c; ch++) {
                        int offset = ch * h * w + row * w + col;
                        float px  = fi.get(row, col, ch)  / 255.0f;
                        float ppx = fpi.get(row, col, ch) / 255.0f;
                        data[offset]         = (px - MEAN[ch]) / STD[ch];
                        data[c*h*w + offset] = Math.max(0f, Math.min(1f, (px - ppx) + 0.5f));
                    }
                }
            }
            fpi.release();
            fp.close();
        } else {
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    for (int ch = 0; ch < c; ch++) {
                        int offset = ch * h * w + row * w + col;
                        float px = fi.get(row, col, ch) / 255.0f;
                        data[offset]         = (px - MEAN[ch]) / STD[ch];
                        data[c*h*w + offset] = 0.5f;
                    }
                }
            }
        }

        fi.release();
        f.close();
        return data;
    }

    private Mat resizeAndRgb(Mat src) {
        Mat resized = new Mat();
        opencv_imgproc.resize(src, resized, new Size(INPUT_SIZE, INPUT_SIZE));
        Mat rgb = new Mat();
        opencv_imgproc.cvtColor(resized, rgb, opencv_imgproc.COLOR_BGR2RGB);
        resized.close();
        Mat f32 = new Mat();
        rgb.convertTo(f32, opencv_core.CV_32FC3);
        rgb.close();
        return f32;
    }

    private LivenessResult runInference(float[] inputData, boolean hasTemp,
                                        float meanDiff, float cv) throws OrtException {
        long[] shape = {1, 6, INPUT_SIZE, INPUT_SIZE};
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(session.getInputNames().iterator().next(), tensor);

        try (OrtSession.Result result = session.run(inputs)) {
            float[][] logits = (float[][]) result.get(0).getValue();
            tensor.close();
            double[] probs  = softmax(new double[]{logits[0][0], logits[0][1]});
            double realProb = probs[1];

            // Gate: model clearly says fake → aux signals cannot rescue it.
            // Prevents high diff_signal from shaking a static image from overriding the model.
            if (realProb < MODEL_GATE) {
                log.info("[AntiSpoof] model gate triggered — realProb={} < {} → FAKE",
                         String.format("%.4f", realProb), MODEL_GATE);
                return new LivenessResult(false, realProb, hasTemp);
            }

            double finalScore;
            if (hasTemp && meanDiff > STATIC_DIFF_THRESHOLD) {
                double diffSignal = sigmoid((meanDiff - DIFF_CENTER) * DIFF_SCALE);
                double cvSignal   = sigmoid((CV_CENTER - cv) * CV_SCALE);
                finalScore = MODEL_WEIGHT * realProb
                           + DIFF_WEIGHT  * diffSignal
                           + CV_WEIGHT    * cvSignal;
                log.info("[AntiSpoof] combined: model={} diff_signal={} cv_signal={} → final={}",
                         String.format("%.4f", realProb),
                         String.format("%.4f", diffSignal),
                         String.format("%.4f", cvSignal),
                         String.format("%.4f", finalScore));
            } else {
                finalScore = realProb;
            }

            boolean live = finalScore >= livenessThreshold;
            if (!live) {
                log.info("[AntiSpoof] FAKE detected — score={} < threshold={}",
                         String.format("%.4f", finalScore), livenessThreshold);
            }
            return new LivenessResult(live, finalScore, hasTemp);
        }
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private Mat decodeMat(byte[] bytes) {
        Mat buf = new Mat(1, bytes.length, opencv_core.CV_8UC1);
        buf.data().put(bytes);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

    private byte[] decodeBase64(String b64) {
        if (b64.contains(",")) b64 = b64.split(",")[1];
        return Base64.getDecoder().decode(b64);
    }

    private double[] softmax(double[] logits) {
        double max = Math.max(logits[0], logits[1]);
        double e0  = Math.exp(logits[0] - max);
        double e1  = Math.exp(logits[1] - max);
        double sum = e0 + e1;
        return new double[]{e0 / sum, e1 / sum};
    }
}
