package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class FoodDetectionConfig {

    private List<String> classNames = new ArrayList<>();

    @PostConstruct
    public void loadClassNames() {
        String yamlPath = "/models/FoodDetectByImage/data.yaml";

        try (InputStream input = getClass().getResourceAsStream(yamlPath)) {
            if (input == null) {
                throw new RuntimeException("Không tìm thấy file data.yaml tại: " + yamlPath);
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            Object namesObj = data.get("names");
            if (namesObj == null) {
                throw new RuntimeException("File data.yaml không có phần 'names'");
            }

            if (namesObj instanceof List) {
                classNames = (List<String>) namesObj;
            } else if (namesObj instanceof Map) {
                // Format map kiểu {0: "Banh-Mi", 1: "Pho", ...}
                Map<Integer, String> map = (Map<Integer, String>) namesObj;
                classNames = new ArrayList<>(map.values());
            } else {
                throw new RuntimeException("Phần 'names' trong data.yaml không đúng format");
            }

            System.out.println("Đã load thành công class names từ data.yaml: " + classNames);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi load data.yaml: " + e.getMessage(), e);
        }
    }

    public List<String> getClassNames() {
        return classNames;
    }
}