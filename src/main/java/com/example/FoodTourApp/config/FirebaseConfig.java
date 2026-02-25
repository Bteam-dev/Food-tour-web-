package com.example.FoodTourApp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized.");
            return;
        }

        try {
            InputStream serviceAccount = loadServiceAccount();
            if (serviceAccount == null) {
                log.warn("Firebase service account file not found at: {}. Push notifications will be disabled.", serviceAccountPath);
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully.");
        } catch (IOException e) {
            log.warn("Failed to initialize Firebase: {}. Push notifications will be disabled.", e.getMessage());
        }
    }

    private InputStream loadServiceAccount() {
        // 1. Try absolute/relative file path first
        File file = new File(serviceAccountPath);
        if (file.exists()) {
            try {
                log.info("Loading Firebase service account from file path: {}", file.getAbsolutePath());
                return new FileInputStream(file);
            } catch (IOException e) {
                log.warn("Could not read file: {}", e.getMessage());
            }
        }

        // 2. Try classpath (resources folder)
        try {
            ClassPathResource resource = new ClassPathResource(serviceAccountPath);
            if (resource.exists()) {
                log.info("Loading Firebase service account from classpath: {}", serviceAccountPath);
                return resource.getInputStream();
            }
        } catch (IOException e) {
            log.warn("Could not load from classpath: {}", e.getMessage());
        }

        return null;
    }
}

