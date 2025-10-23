package com.example.FoodTourApp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Lưu file và trả về đường dẫn public
     * @param file File cần lưu
     * @param subfolder Thư mục con (ví dụ: "shops/123" hoặc "products/456")
     * @return Đường dẫn public của file (ví dụ: "/uploads/shops/123/logo-uuid.jpg")
     */
    public String storeFile(MultipartFile file, String subfolder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate file type (chỉ cho phép jpg, jpeg, png)
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
            !contentType.equals("image/jpg") && !contentType.equals("image/png"))) {
            throw new IOException("Chỉ chấp nhận file ảnh định dạng JPG, JPEG hoặc PNG");
        }

        // Validate file size (tối đa 5MB)
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new IOException("Kích thước file không được vượt quá 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("Tên file không hợp lệ");
        }

        originalFilename = StringUtils.cleanPath(originalFilename);
        String extension = "";

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // Tạo tên file unique
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;

        // Tạo thư mục nếu chưa tồn tại
        Path targetFolder = Paths.get(uploadDir).resolve(subfolder).toAbsolutePath().normalize();
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved successfully: {}", targetPath);
        } catch (IOException e) {
            log.error("Could not store file {}: {}", originalFilename, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về đường dẫn public để frontend có thể truy cập
        return "/" + uploadDir + "/" + subfolder + "/" + filename;
    }

    /**
     * Lưu nhiều file cùng lúc
     * @param files Mảng các file cần lưu
     * @param subfolder Thư mục con
     * @return Danh sách đường dẫn public của các file
     */
    public List<String> storeFiles(MultipartFile[] files, String subfolder) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storeFile(file, subfolder);
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * Xóa file
     * @param filePath Đường dẫn file cần xóa (ví dụ: "/uploads/shops/123/logo.jpg")
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        try {
            // Remove leading "/" if exists
            if (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }

            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            Files.deleteIfExists(path);
            log.info("File deleted successfully: {}", path);
        } catch (IOException e) {
            log.error("Could not delete file {}: {}", filePath, e.getMessage());
        }
    }
}
