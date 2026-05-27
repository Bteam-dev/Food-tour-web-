package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.service.MessageEncryptionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MessageEncryptionServiceImpl implements MessageEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(MessageEncryptionServiceImpl.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${encryption.secret-key}")
    private String secretKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption key must be 256-bit (32 bytes). Got: " + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("[Encryption] AES-256-GCM encryption service initialized");
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            log.error("[Encryption] Failed to encrypt: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) return encryptedText;

        int colonIndex = encryptedText.indexOf(':');
        if (colonIndex <= 0 || colonIndex == encryptedText.length() - 1) {
            return encryptedText;
        }

        String ivPart = encryptedText.substring(0, colonIndex);
        String cipherPart = encryptedText.substring(colonIndex + 1);

        try {
            byte[] iv = Base64.getDecoder().decode(ivPart);
            byte[] cipherText = Base64.getDecoder().decode(cipherPart);

            if (iv.length != GCM_IV_LENGTH) return encryptedText;

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return encryptedText;
        } catch (Exception e) {
            log.warn("[Encryption] Decryption failed, returning original text: {}", e.getMessage());
            return encryptedText;
        }
    }

    @Override
    public byte[] encryptBytes(byte[] data) {
        if (data == null || data.length == 0) return data;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(data);

            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + cipherText.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return buffer.array();
        } catch (Exception e) {
            log.error("[Encryption] Failed to encrypt bytes: {}", e.getMessage());
            throw new RuntimeException("Byte encryption failed", e);
        }
    }

    @Override
    public byte[] decryptBytes(byte[] data) {
        if (data == null || data.length == 0) return data;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int ivLength = buffer.getInt();

            if (ivLength != GCM_IV_LENGTH || data.length < 4 + ivLength + 1) {
                return data;
            }

            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            log.warn("[Encryption] Byte decryption failed, returning original: {}", e.getMessage());
            return data;
        }
    }
}
