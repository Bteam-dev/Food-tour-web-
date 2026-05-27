package com.example.FoodTourApp.service.impl;

import ai.onnxruntime.*;
import com.example.FoodTourApp.service.FaceEmbeddingExtractorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FaceEmbeddingExtractorServiceImpl implements FaceEmbeddingExtractorService {

    private static final Logger log = LoggerFactory.getLogger(FaceEmbeddingExtractorServiceImpl.class);

    private static final int FACE_SIZE = 112;

    private static final float[] ARCFACE_TEMPLATE = {
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f
    };

    @Value("${face.model.arcface.path}")
    private String arcfacePath;

    @Value("${face.model.yunet.path}")
    private String yunetPath;

    private OrtEnvironment env;
    private OrtSession   arcfaceSession;
    private FaceDetectorYN faceDetector;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            opts.setInterOpNumThreads(1);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            arcfaceSession = env.createSession(arcfacePath, opts);
            log.info("[FaceAuth] ArcFace loaded: {}", arcfacePath);
        } catch (Exception e) {
            log.error("[FaceAuth] ArcFace load failed: {}", e.getMessage());
        }

        try {
            faceDetector = FaceDetectorYN.create(
                    yunetPath, "", new Size(320, 320), 0.85f, 0.3f, 5000, 0, 0);
            log.info("[FaceAuth] YuNet loaded: {}", yunetPath);
        } catch (Exception e) {
            log.error("[FaceAuth] YuNet load failed: {}. Face auth will reject all requests.", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try { if (arcfaceSession != null) arcfaceSession.close(); } catch (OrtException ignored) {}
        try { if (env != null) env.close(); }            catch (Exception ignored) {}
    }

    @Override
    public float[] extractEmbedding(String base64Image) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(cleanBase64(base64Image));
        Mat img = decodeMat(bytes);
        if (img.empty()) throw new IllegalArgumentException("Cannot decode image");

        Mat aligned = detectAndAlign(img);
        img.close();
        if (aligned == null) return null;

        float[] input = preprocessArcFace(aligned);
        aligned.close();
        return runArcFace(input);
    }

    @Override
    public float[] averageEmbeddings(List<float[]> embeddings) {
        int dim = embeddings.get(0).length;
        float[] avg = new float[dim];
        for (float[] emb : embeddings)
            for (int i = 0; i < dim; i++) avg[i] += emb[i];
        for (int i = 0; i < dim; i++) avg[i] /= embeddings.size();
        return l2Normalize(avg);
    }

    @Override
    public double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private Mat detectAndAlign(Mat img) {
        if (faceDetector == null) {
            log.warn("[FaceAuth] YuNet not loaded — rejecting frame");
            return null;
        }

        Mat detections = new Mat();
        synchronized (faceDetector) {
            faceDetector.setInputSize(new Size(img.cols(), img.rows()));
            faceDetector.detect(img, detections);
        }

        if (detections.empty() || detections.rows() == 0) {
            detections.close();
            log.debug("[FaceAuth] No face detected by YuNet");
            return null;
        }

        FloatIndexer idx = detections.createIndexer();

        int best = 0;
        float bestScore = idx.get(0L, 14L);
        for (int i = 1; i < detections.rows(); i++) {
            float s = idx.get((long) i, 14L);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        log.debug("[FaceAuth] YuNet best face score={}", String.format("%.3f", bestScore));

        float[] kps = new float[10];
        for (int j = 0; j < 10; j++)
            kps[j] = idx.get((long) best, (long) (4 + j));

        idx.release();
        detections.close();

        return alignFace(img, kps);
    }

    private Mat alignFace(Mat img, float[] kps) {
        Mat srcPts = new Mat(5, 1, opencv_core.CV_32FC2);
        Mat dstPts = new Mat(5, 1, opencv_core.CV_32FC2);

        FloatIndexer si = srcPts.createIndexer();
        FloatIndexer di = dstPts.createIndexer();
        for (int i = 0; i < 5; i++) {
            si.put((long) i, 0L, 0L, kps[i * 2]);
            si.put((long) i, 0L, 1L, kps[i * 2 + 1]);
            di.put((long) i, 0L, 0L, ARCFACE_TEMPLATE[i * 2]);
            di.put((long) i, 0L, 1L, ARCFACE_TEMPLATE[i * 2 + 1]);
        }
        si.release();
        di.release();

        Mat inliers = new Mat();
        Mat M = opencv_calib3d.estimateAffinePartial2D(
                srcPts, dstPts, inliers,
                opencv_calib3d.RANSAC, 3.0, 2000, 0.99, 10);
        srcPts.close(); dstPts.close(); inliers.close();

        if (M == null || M.empty()) {
            if (M != null) M.close();
            log.warn("[FaceAuth] estimateAffinePartial2D returned empty transform");
            return null;
        }

        Mat aligned = new Mat();
        opencv_imgproc.warpAffine(img, aligned, M,
                new Size(FACE_SIZE, FACE_SIZE),
                opencv_imgproc.INTER_LINEAR,
                opencv_core.BORDER_REFLECT,
                new Scalar());
        M.close();
        return aligned;
    }

    private float[] preprocessArcFace(Mat face) {
        Mat rgb = new Mat();
        opencv_imgproc.cvtColor(face, rgb, opencv_imgproc.COLOR_BGR2RGB);
        Mat rgbF = new Mat();
        rgb.convertTo(rgbF, opencv_core.CV_32FC3);
        rgb.close();

        int h = FACE_SIZE, w = FACE_SIZE, c = 3;
        float[] data = new float[c * h * w];
        FloatIndexer fi = rgbF.createIndexer();
        for (int row = 0; row < h; row++)
            for (int col = 0; col < w; col++)
                for (int ch = 0; ch < c; ch++)
                    data[ch * h * w + row * w + col] =
                            (fi.get(row, col, ch) / 127.5f) - 1.0f;
        fi.release();
        rgbF.close();
        return data;
    }

    private float[] runArcFace(float[] inputData) throws OrtException {
        long[] shape = {1, 3, FACE_SIZE, FACE_SIZE};
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(arcfaceSession.getInputNames().iterator().next(), tensor);
        try (OrtSession.Result result = arcfaceSession.run(inputs)) {
            float[][] output = (float[][]) result.get(0).getValue();
            tensor.close();
            return l2Normalize(output[0]);
        }
    }

    private float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    private Mat decodeMat(byte[] bytes) {
        Mat buf = new Mat(1, bytes.length, opencv_core.CV_8UC1);
        buf.data().put(bytes);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

    private String cleanBase64(String b64) {
        return b64.contains(",") ? b64.split(",")[1] : b64;
    }
}
