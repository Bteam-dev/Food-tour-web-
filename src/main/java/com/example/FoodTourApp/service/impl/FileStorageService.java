package com.example.FoodTourApp.service.impl;

import lombok.extern.slf4j.Slf4j;
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

    // Các thư mục lưu trữ cố định
    private static final String BASE_DIR = "D:\\Project\\BackEnd\\FoodTourApp_BE\\StorageFile";
    private static final String PRODUCT_DIR = BASE_DIR + "\\Product";
    private static final String FILE_MESSAGE_DIR = BASE_DIR + "\\FileMessage";
    private static final String AVATAR_DIR = BASE_DIR + "\\Avatar";

    /**
     * Enum định nghĩa các loại file storage
     */
    public enum FileCategory {
        PRODUCT,
        FILE_MESSAGE,
        AVATAR
    }

    /**
     * Lưu file theo category và trả về đường dẫn public
     * @param file File cần lưu
     * @param category Loại file (PRODUCT, FILE_MESSAGE, AVATAR)
     * @return Đường dẫn đầy đủ của file đã lưu
     */
    public String storeFile(MultipartFile file, FileCategory category) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
            !contentType.equals("image/jpg") &&
            !contentType.equals("image/png") &&
            !contentType.equals("application/pdf"))) {
            throw new IOException("Chỉ chấp nhận file ảnh (JPG, JPEG, PNG) hoặc PDF");
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

        // Chọn thư mục dựa trên category
        String targetDir = getDirectoryByCategory(category);
        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();

        // Tạo thư mục nếu chưa tồn tại
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved successfully to {}: {}", category, targetPath);
        } catch (IOException e) {
            log.error("Could not store file {} to {}: {}", originalFilename, category, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về đường dẫn đầy đủ của file
        return targetPath.toString();
    }

    /**
     * DEPRECATED - Sử dụng storeFile(MultipartFile file, FileCategory category) thay thế
     * Giữ lại để backward compatibility
     */
    @Deprecated
    public String storeFile(MultipartFile file, String subfolder) throws IOException {
        // Map subfolder cũ sang FileCategory mới
        FileCategory category;
        if (subfolder.contains("product")) {
            category = FileCategory.PRODUCT;
        } else if (subfolder.contains("message") || subfolder.contains("chat")) {
            category = FileCategory.FILE_MESSAGE;
        } else if (subfolder.contains("avatar") || subfolder.contains("user") || subfolder.contains("shop")) {
            category = FileCategory.AVATAR;
        } else {
            category = FileCategory.PRODUCT; // Default
        }

        return storeFile(file, category);
    }

    /**
     * Lưu nhiều file cùng lúc
     * @param files Mảng các file cần lưu
     * @param category Loại file
     * @return Danh sách đường dẫn đầy đủ của các file
     */
    public List<String> storeFiles(MultipartFile[] files, FileCategory category) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String path = storeFile(file, category);
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * DEPRECATED - Backward compatibility
     */
    @Deprecated
    public List<String> storeFiles(MultipartFile[] files, String subfolder) throws IOException {
        FileCategory category = getCategoryFromSubfolder(subfolder);
        return storeFiles(files, category);
    }

    /**
     * Xóa file
     * @param filePath Đường dẫn đầy đủ của file cần xóa
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            Files.deleteIfExists(path);
            log.info("File deleted successfully: {}", path);
        } catch (IOException e) {
            log.error("Could not delete file {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Lấy thư mục dựa trên category
     */
    private String getDirectoryByCategory(FileCategory category) {
        switch (category) {
            case PRODUCT:
                return PRODUCT_DIR;
            case FILE_MESSAGE:
                return FILE_MESSAGE_DIR;
            case AVATAR:
                return AVATAR_DIR;
            default:
                return PRODUCT_DIR;
        }
    }

    /**
     * Map subfolder cũ sang FileCategory (để backward compatibility)
     */
    private FileCategory getCategoryFromSubfolder(String subfolder) {
        if (subfolder == null) {
            return FileCategory.PRODUCT;
        }

        subfolder = subfolder.toLowerCase();
        if (subfolder.contains("product")) {
            return FileCategory.PRODUCT;
        } else if (subfolder.contains("message") || subfolder.contains("chat")) {
            return FileCategory.FILE_MESSAGE;
        } else if (subfolder.contains("avatar") || subfolder.contains("user") || subfolder.contains("shop")) {
            return FileCategory.AVATAR;
        }

        return FileCategory.PRODUCT;
    }

    /**
     * Lấy đường dẫn tương đối để trả về cho client (nếu cần)
     * @param fullPath Đường dẫn đầy đủ
     * @return Đường dẫn tương đối từ thư mục base
     */
    public String getRelativePath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }

        return fullPath.replace(BASE_DIR, "").replace("\\", "/");
    }
}
