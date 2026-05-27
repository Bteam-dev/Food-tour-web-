package com.example.FoodTourApp.service;

/**
 * AES-256-GCM encryption service for message content and files.
 *
 * String format: Base64(IV):Base64(ciphertext+GCM_tag)
 * Bytes format:  [4-byte IV length][IV bytes][ciphertext+GCM_tag bytes]
 *
 * Backward compatible: decrypt methods gracefully return original data
 * if input is not in encrypted format (supports existing plaintext data).
 */
public interface MessageEncryptionService {

    String encrypt(String plainText);

    String decrypt(String encryptedText);

    byte[] encryptBytes(byte[] data);

    byte[] decryptBytes(byte[] data);
}
