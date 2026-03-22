package com.example.FoodTourApp.service.impl;

import ai.onnxruntime.*;
import com.example.FoodTourApp.config.FoodDetectionConfig;
import com.example.FoodTourApp.service.FoodDetectionService;
import jakarta.annotation.PostConstruct;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class FoodDetectionServiceImpl implements FoodDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(FoodDetectionServiceImpl.class);

    private final FoodDetectionConfig config;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> classNames;

    public FoodDetectionServiceImpl(FoodDetectionConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() throws Exception {
        // Load toàn bộ native lib của OpenCV qua JavaCPP Loader
        // Không cần cài DLL tay, tự extract từ JAR
        Loader.load(opencv_core.class);
        Loader.load(opencv_imgcodecs.class);
        Loader.load(opencv_imgproc.class);

        env = OrtEnvironment.getEnvironment();

        // Copy best.onnx ra file tạm để lấy đường dẫn Windows hợp lệ
        // getResource().getPath() trả về "/D:/..." không hợp lệ trên Windows
        try (InputStream modelStream = getClass().getResourceAsStream("/models/FoodDetectByImage/best.onnx")) {
            if (modelStream == null) {
                throw new RuntimeException("Không tìm thấy file best.onnx trong resources/models/FoodDetectByImage/");
            }
            File tempModel = File.createTempFile("best_model", ".onnx");
            tempModel.deleteOnExit();
            Files.copy(modelStream, tempModel.toPath(), StandardCopyOption.REPLACE_EXISTING);

            session = env.createSession(tempModel.getAbsolutePath(), new OrtSession.SessionOptions());
            logger.info("Loaded ONNX model từ: {}", tempModel.getAbsolutePath());
        }

        // Load class names từ data.yaml
        this.classNames = config.getClassNames();
        if (classNames == null || classNames.isEmpty()) {
            throw new RuntimeException("Không load được danh sách class names từ data.yaml");
        }

        logger.info("FoodDetectionService khởi tạo thành công với {} classes: {}", classNames.size(), classNames);
    }

    @Override
    public List<String> detectFoodNames(MultipartFile imageFile) {
        List<String> detected = new ArrayList<>();

        try {
            // Lưu tạm file ảnh upload
            File tempFile = File.createTempFile("upload_img", ".jpg");
            tempFile.deleteOnExit();
            imageFile.transferTo(tempFile);

            // Đọc ảnh bằng JavaCV (bytedeco)
            Mat img = opencv_imgcodecs.imread(tempFile.getAbsolutePath());
            if (img.empty()) {
                logger.error("Không đọc được ảnh: {}", tempFile.getAbsolutePath());
                return detected;
            }

            // Preprocess: BGR -> RGB, resize 640x640, normalize /255
            opencv_imgproc.cvtColor(img, img, opencv_imgproc.COLOR_BGR2RGB);
            opencv_imgproc.resize(img, img, new Size(640, 640));

            Mat floatImg = new Mat();
            img.convertTo(floatImg, opencv_core.CV_32FC3, 1.0 / 255.0, 0);

            // Mat sau convertTo CV_32FC3 -> createBuffer() trả thẳng FloatBuffer
            int H = 640, W = 640, C = 3;
            FloatBuffer fb = (FloatBuffer) floatImg.createBuffer();
            float[] hwcData = new float[H * W * C];
            fb.get(hwcData);

            // Chuyển HWC -> CHW (ONNX YOLOv8 cần CHW)
            float[] chwData = new float[C * H * W];
            for (int h = 0; h < H; h++) {
                for (int w = 0; w < W; w++) {
                    for (int c = 0; c < C; c++) {
                        chwData[c * H * W + h * W + w] = hwcData[(h * W + w) * C + c];
                    }
                }
            }

            FloatBuffer buffer = FloatBuffer.wrap(chwData);
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, new long[]{1, C, H, W});

            // Run inference
            Map<String, OnnxTensor> inputs = Collections.singletonMap("images", inputTensor);
            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue outputValue = result.get(0);
                if (!(outputValue instanceof OnnxTensor outputTensor)) {
                    throw new RuntimeException("Output không phải OnnxTensor");
                }

                long[] shape = outputTensor.getInfo().getShape();
                Object rawValue = outputTensor.getValue();
                if (!(rawValue instanceof float[][][] output)) {
                    throw new RuntimeException("Output value không phải float[][][], shape: " + Arrays.toString(shape));
                }

                // shape: [batch, 4+num_classes, num_detections] - YOLOv8 format
                int batch = (int) shape[0];
                int numAttr = (int) shape[1]; // 4 box coords + num_classes
                int numDetections = (int) shape[2];
                int numClasses = numAttr - 4;

                Set<String> detectedSet = new LinkedHashSet<>();
                for (int b = 0; b < batch; b++) {
                    for (int i = 0; i < numDetections; i++) {
                        // YOLOv8: không có conf riêng, chỉ có class scores từ index 4
                        int classId = -1;
                        float maxScore = 0.5f; // threshold
                        for (int c = 4; c < numAttr; c++) {
                            float score = output[b][c][i];
                            if (score > maxScore) {
                                maxScore = score;
                                classId = c - 4;
                            }
                        }

                        if (classId >= 0 && classId < classNames.size()) {
                            detectedSet.add(classNames.get(classId));
                        }
                    }
                }
                detected.addAll(detectedSet);
            }

            tempFile.delete();
        } catch (Exception e) {
            logger.error("Lỗi khi detect ảnh: {}", e.getMessage(), e);
        }

        return detected;
    }
}