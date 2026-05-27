package com.example.FoodTourApp.service.impl;

import ai.onnxruntime.*;
import com.example.FoodTourApp.service.HeadPoseService;
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
import java.util.Random;

@Service
public class HeadPoseServiceImpl implements HeadPoseService {

    private static final Logger log = LoggerFactory.getLogger(HeadPoseServiceImpl.class);

    private static final int INPUT_SIZE = 224;
    private static final float YAW_THRESHOLD   = 25.0f;
    private static final float PITCH_THRESHOLD = 15.0f;

    @Value("${face.model.headpose.path}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;

    private final Random random = new Random();

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            opts.setInterOpNumThreads(1);
            session = env.createSession(modelPath, opts);
            modelLoaded = true;
            log.info("[HeadPose] Model loaded from {}", modelPath);
        } catch (Exception e) {
            log.warn("[HeadPose] Model not loaded: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) session.close();
        } catch (OrtException ignored) {}
    }

    @Override
    public HeadAction randomChallenge() {
        HeadAction[] actions = HeadAction.values();
        return actions[random.nextInt(actions.length)];
    }

    @Override
    public PoseResult estimatePose(String base64Image) throws Exception {
        if (!modelLoaded) return null;
        byte[] bytes = Base64.getDecoder().decode(cleanBase64(base64Image));
        return estimatePoseFromBytes(bytes);
    }

    @Override
    public PoseResult estimatePoseFromBytes(byte[] imageBytes) throws Exception {
        if (!modelLoaded) return null;

        Mat img = decodeMat(imageBytes);
        if (img.empty()) throw new IllegalArgumentException("Cannot decode image");

        float[] input = preprocess(img);
        img.close();

        return runInference(input);
    }

    @Override
    public boolean verifyChallenge(PoseResult pose, HeadAction required) {
        if (pose == null) return false;
        return switch (required) {
            case TURN_LEFT  -> pose.yaw()   < -YAW_THRESHOLD;
            case TURN_RIGHT -> pose.yaw()   > YAW_THRESHOLD;
            case LOOK_UP    -> pose.pitch() > PITCH_THRESHOLD;
            case LOOK_DOWN  -> pose.pitch() < -PITCH_THRESHOLD;
        };
    }

    private Mat decodeMat(byte[] bytes) {
        Mat buf = new Mat(1, bytes.length, opencv_core.CV_8UC1);
        buf.data().put(bytes);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

    private float[] preprocess(Mat img) {
        Mat resized = new Mat();
        opencv_imgproc.resize(img, resized, new Size(INPUT_SIZE, INPUT_SIZE));

        Mat rgb = new Mat();
        opencv_imgproc.cvtColor(resized, rgb, opencv_imgproc.COLOR_BGR2RGB);
        resized.close();

        int h = INPUT_SIZE, w = INPUT_SIZE, c = 3;
        float[] data = new float[c * h * w];

        Mat rgbF = new Mat();
        rgb.convertTo(rgbF, opencv_core.CV_32FC3);
        rgb.close();

        FloatIndexer indexer = rgbF.createIndexer();
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                for (int ch = 0; ch < c; ch++) {
                    data[ch * h * w + row * w + col] = indexer.get(row, col, ch);
                }
            }
        }
        indexer.release();
        rgbF.close();
        return data;
    }

    private PoseResult runInference(float[] inputData) throws OrtException {
        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(session.getInputNames().iterator().next(), tensor);

        try (OrtSession.Result result = session.run(inputs)) {
            float[][] output = (float[][]) result.get(0).getValue();
            tensor.close();
            return new PoseResult(output[0][0], output[0][1], output[0][2]);
        }
    }

    private String cleanBase64(String b64) {
        if (b64.contains(",")) return b64.split(",")[1];
        return b64;
    }
}
