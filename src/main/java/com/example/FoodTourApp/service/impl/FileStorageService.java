package com.example.FoodTourApp.service.impl;

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

    // Các thư mục lưu trữ - sử dụng baseDir từ environment variable
    @Value("${FILE_STORAGE_BASE_DIR}")
    private String baseDir;

    // Base URL để truy cập file từ frontend
    private static final String BASE_URL = "/uploads";

    /**
     * Enum định ngh��a các loại file storage
     * Chia nhỏ để dễ quản lý và theo dõi
     */
    public enum FileCategory {
        PRODUCT_IMAGE,      // Ảnh sản phẩm
        SHOP_LOGO,          // Logo cửa hàng
        SHOP_BANNER,        // Banner cửa hàng
        REVIEW_IMAGE,       // Ảnh đánh giá (review)
        REVIEW_REPLY_IMAGE, // Ảnh phản hồi đánh giá (review reply)
        USER_AVATAR,        // Avatar người dùng
        FILE_MESSAGE,       // File gửi trong chat
        BUSINESS_LICENSE,   // Ảnh giấy phép kinh doanh
        ID_CARD             // Ảnh căn cước công dân
    }

    /**
     * Lưu file theo category và trả về URL tương đối (accessible URL)
     * @param file File cần lưu
     * @param category Loại file
     * @return URL tương đối có thể truy cập từ frontend
     */
    public String storeFile(MultipartFile file, FileCategory category) throws IOException {
        return storeFile(file, category, null);
    }

    /**
     * Lưu file theo category và subfolder ID (để phân biệt theo product/shop/user)
     * @param file File cần lưu
     * @param category Loại file
     * @param subfolderId ID của product/shop/user (để tạo thư mục con) - DEPRECATED, use storeFileWithHierarchy
     * @return URL tương đối có thể truy cập từ frontend
     */
    @Deprecated
    public String storeFile(MultipartFile file, FileCategory category, String subfolderId) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // FILE_MESSAGE cho phép tất cả loại file (ảnh, video, audio, pdf, zip...)
        if (category == FileCategory.FILE_MESSAGE) {
            return storeChatFile(file, subfolderId);
        }

        // Validate file type - chỉ chấp nhận ảnh cho các category khác
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Chỉ chấp nhận file ảnh (JPG, JPEG, PNG, WEBP, GIF, BMP, SVG, etc.)");
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

        // Nếu có subfolderId, tạo thêm thư mục con (VD: ProductImage/product_5/)
        if (subfolderId != null && !subfolderId.isEmpty()) {
            targetDir = targetDir + "\\" + subfolderId;
        }

        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();

        // Tạo thư mục nếu chưa tồn tại
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved successfully to {} (subfolder: {}): {}", category, subfolderId, targetPath);
        } catch (IOException e) {
            log.error("Could not store file {} to {}: {}", originalFilename, category, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về URL tương đối thay vì đường dẫn tuyệt đối
        String relativePath = convertToRelativeUrl(targetPath.toString());
        log.info("File URL for frontend: {}", relativePath);
        return relativePath;
    }

    /**
     * DEPRECATED - Sử dụng storeFile(MultipartFile file, FileCategory category) thay thế
     * Giữ lại để backward compatibility
     */
    @Deprecated
    public String storeFile(MultipartFile file, String subfolder) throws IOException {
        FileCategory category = getCategoryFromSubfolder(subfolder);
        return storeFile(file, category);
    }

    /**
     * Lưu nhiều file cùng lúc
     * @param files Mảng các file cần lưu
     * @param category Loại file
     * @return Danh sách URL tương đối có thể truy cập từ frontend
     */
    public List<String> storeFiles(MultipartFile[] files, FileCategory category) throws IOException {
        return storeFiles(files, category, null);
    }

    /**
     * Lưu nhiều file cùng lúc với subfolder ID
     * @param files Mảng các file cần lưu
     * @param category Loại file
     * @param subfolderId ID của product/shop/user
     * @return Danh sách URL tương đối có thể truy cập từ frontend
     */
    public List<String> storeFiles(MultipartFile[] files, FileCategory category, String subfolderId) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storeFile(file, category, subfolderId);
                paths.add(url);
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
     * Xóa file bằng URL tương đối hoặc đường dẫn tuyệt đối
     * @param filePathOrUrl Đường dẫn đầy đủ hoặc URL tương đối của file cần xóa
     */
    public void deleteFile(String filePathOrUrl) {
        if (filePathOrUrl == null || filePathOrUrl.isEmpty()) {
            return;
        }

        try {
            // Nếu là URL tương đối, chuyển về đường dẫn tuyệt đối
            String fullPath = filePathOrUrl.startsWith(BASE_URL)
                ? convertUrlToFullPath(filePathOrUrl)
                : filePathOrUrl;

            Path path = Paths.get(fullPath).toAbsolutePath().normalize();
            Files.deleteIfExists(path);
            log.info("File deleted successfully: {}", path);
        } catch (IOException e) {
            log.error("Could not delete file {}: {}", filePathOrUrl, e.getMessage());
        }
    }

    /**
     * Lưu file chat – chấp nhận mọi loại file (image, video, audio, PDF, v.v.)
     * Đường dẫn: FileMessage/user_{userId}\conversation_{conversationId}\
     * Giới hạn 50MB.
     *
     * @param file        file cần lưu
     * @param userId      ID của người gửi
     * @param conversationId  ID của cuộc trò chuyện
     */
    public String storeChatFile(MultipartFile file, Integer userId, Long conversationId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        long maxSize = 50L * 1024 * 1024; // 50 MB
        if (file.getSize() > maxSize) {
            throw new IOException("Kích thước file không được vượt quá 50MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("Tên file không hợp lệ");
        }

        originalFilename = StringUtils.cleanPath(originalFilename);
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;

        // D:\...\FileMessage/user_{userId}\conversation_{conversationId}\
        String targetDir = baseDir + "\\FileMessage"
                + "\\user_" + userId
                + "\\conversation_" + conversationId;

        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Chat file saved to {}: {}", targetDir, targetPath);
        } catch (IOException e) {
            log.error("Could not store chat file {}: {}", originalFilename, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        String relativePath = convertToRelativeUrl(targetPath.toString());
        log.info("Chat file URL: {}", relativePath);
        return relativePath;
    }

    /**
     * @deprecated Dùng storeChatFile(file, userId, conversationId) thay thế
     */
    @Deprecated
    public String storeChatFile(MultipartFile file, String subfolderId) throws IOException {
        // fallback không biết userId/conversationId → lưu thẳng vào FileMessage\subfolderId\
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }
        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) throw new IOException("Kích thước file không được vượt quá 50MB");

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) extension = originalFilename.substring(dotIndex).toLowerCase();

        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        String targetDir = baseDir + "\\FileMessage" + (subfolderId != null ? "\\" + subfolderId : "");
        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();
        Files.createDirectories(targetFolder);
        Path targetPath = targetFolder.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return convertToRelativeUrl(targetPath.toString());
    }

    /**
     * Xác định MessageType dựa trên MIME type của file
     */
    public static String detectMessageType(String contentType) {
        if (contentType == null) return "FILE";
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "FILE";
    }

    /**
     * Lấy thư mục dựa trên category
     */
    private String getDirectoryByCategory(FileCategory category) {
        switch (category) {
            case PRODUCT_IMAGE:
                return baseDir + "\\ProductImage";
            case SHOP_LOGO:
                return baseDir + "\\ShopLogo";
            case SHOP_BANNER:
                return baseDir + "\\ShopBanner";
            case REVIEW_IMAGE:
                return baseDir + "\\ReviewImage";
            case REVIEW_REPLY_IMAGE:
                return baseDir + "\\ReviewReplyImage";
            case USER_AVATAR:
                return baseDir + "\\UserAvatar";
            case FILE_MESSAGE:
                return baseDir + "\\FileMessage";
            case BUSINESS_LICENSE:
                return baseDir + "\\BusinessLicense";
            case ID_CARD:
                return baseDir + "\\IdCard";
            default:
                return baseDir + "\\ProductImage";
        }
    }

    /**
     * Map subfolder cũ sang FileCategory (để backward compatibility)
     */
    private FileCategory getCategoryFromSubfolder(String subfolder) {
        if (subfolder == null) {
            return FileCategory.PRODUCT_IMAGE;
        }

        subfolder = subfolder.toLowerCase();
        if (subfolder.contains("product")) {
            return FileCategory.PRODUCT_IMAGE;
        } else if (subfolder.contains("message") || subfolder.contains("chat")) {
            return FileCategory.FILE_MESSAGE;
        } else if (subfolder.contains("shop") && subfolder.contains("banner")) {
            return FileCategory.SHOP_BANNER;
        } else if (subfolder.contains("shop") && subfolder.contains("logo")) {
            return FileCategory.SHOP_LOGO;
        } else if (subfolder.contains("shop")) {
            return FileCategory.SHOP_LOGO; // Default cho shop
        } else if (subfolder.contains("review")) {
            return FileCategory.REVIEW_IMAGE;
        } else if (subfolder.contains("avatar") || subfolder.contains("user")) {
            return FileCategory.USER_AVATAR;
        }

        return FileCategory.PRODUCT_IMAGE;
    }

    /**
     * Chuyển đường dẫn tuyệt đối thành URL tương đối
     * VD: D:\Project\BackEnd\FoodTourApp_BE\StorageFile\ProductImage\shop_1\abc.jpg
     *     -> /uploads/ProductImage/shop_1/abc.jpg
     */
    private String convertToRelativeUrl(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }

        Path full = Paths.get(fullPath).toAbsolutePath().normalize();
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();

        Path relative = base.relativize(full);
        String relativeStr = relative.toString().replace("\\", "/");

        // Đảm bảo bắt đầu bằng /
        if (!relativeStr.startsWith("/")) {
            relativeStr = "/" + relativeStr;
        }

        return BASE_URL + relativeStr;
    }

    /**
     * Chuyển URL tương đối thành đường dẫn tuyệt đối
     * VD: /uploads/ProductImage/shop_1/abc.jpg
     *     -> D:\Project\BackEnd\FoodTourApp_BE\StorageFile\ProductImage\shop_1\abc.jpg
     */
    private String convertUrlToFullPath(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return null;
        }

        String path = relativeUrl.replace(BASE_URL, "")
                .replace("/", "\\");  // Windows dùng \, nhưng Paths sẽ xử lý

        return baseDir + path;
    }

    /**
     * Lấy đường dẫn tương đối để trả về cho client (nếu cần)
     * @param fullPath Đường dẫn đầy đủ
     * @return Đường dẫn tương đối từ thư mục base
     */
    public String getRelativePath(String fullPath) {
        return convertToRelativeUrl(fullPath);
    }

    /**
     * Lưu file nhạy cảm (IdCard, BusinessLicense) và trả về relative path
     * (không phải public URL) để sau này tạo signed URL
     *
     * @param files Mảng các file cần lưu
     * @param category Loại file (ID_CARD hoặc BUSINESS_LICENSE)
     * @param subfolderId ID của user/shop - DEPRECATED, use storeSensitiveFilesWithHierarchy
     * @return Danh sách relative path (vd: "IdCard/user_3/abc.jpg")
     */
    @Deprecated
    public List<String> storeSensitiveFiles(MultipartFile[] files, FileCategory category, String subfolderId) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        // Chỉ cho phép ID_CARD và BUSINESS_LICENSE
        if (category != FileCategory.ID_CARD && category != FileCategory.BUSINESS_LICENSE) {
            throw new IllegalArgumentException("This method only supports ID_CARD and BUSINESS_LICENSE");
        }

        List<String> relativePaths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String relativePath = storeSensitiveFile(file, category, subfolderId);
                relativePaths.add(relativePath);
            }
        }
        return relativePaths;
    }

    /**
     * Lưu một file nhạy cảm và trả về relative path - DEPRECATED
     */
    @Deprecated
    private String storeSensitiveFile(MultipartFile file, FileCategory category, String subfolderId) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate file type - chỉ chấp nhận ảnh
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Chỉ chấp nhận file ảnh (JPG, JPEG, PNG, WEBP, GIF, BMP, SVG, etc.)");
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

        // Nếu có subfolderId, tạo thêm thư mục con (VD: IdCard/user_3/)
        if (subfolderId != null && !subfolderId.isEmpty()) {
            targetDir = targetDir + "\\" + subfolderId;
        }

        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();

        // Tạo thư mục nếu chưa tồn tại
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về relative path (không phải public URL)
        // VD: "IdCard/user_3/abc.jpg" hoặc "BusinessLicense/shop_5/xyz.jpg"
        String categoryName = category == FileCategory.ID_CARD ? "IdCard" : "BusinessLicense";
        String relativePath = categoryName + "/" + (subfolderId != null ? subfolderId + "/" : "") + filename;

        return relativePath;
    }

    /**
     * Lưu file nhạy cảm (IdCard, BusinessLicense) với cấu trúc phân cấp
     * Trả về relative path để tạo signed URL sau
     * 
     * @param files Mảng các file cần lưu
     * @param category Loại file (ID_CARD hoặc BUSINESS_LICENSE)
     * @param hierarchyPath Cấu trúc thư mục phân cấp (VD: "123/456" cho user_id/seller_approval_id)
     * @return Danh sách relative path (vd: "IdCard/123/456/abc.jpg")
     */
    public List<String> storeSensitiveFilesWithHierarchy(MultipartFile[] files, FileCategory category, String hierarchyPath) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        // Chỉ cho phép ID_CARD và BUSINESS_LICENSE
        if (category != FileCategory.ID_CARD && category != FileCategory.BUSINESS_LICENSE) {
            throw new IllegalArgumentException("This method only supports ID_CARD and BUSINESS_LICENSE");
        }

        List<String> relativePaths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String relativePath = storeSensitiveFileWithHierarchy(file, category, hierarchyPath);
                relativePaths.add(relativePath);
            }
        }
        return relativePaths;
    }

    /**
     * Lưu một file nhạy cảm với cấu trúc phân cấp và trả về relative path
     */
    private String storeSensitiveFileWithHierarchy(MultipartFile file, FileCategory category, String hierarchyPath) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate file type - chỉ chấp nhận ảnh
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Chỉ chấp nhận file ảnh (JPG, JPEG, PNG, WEBP, GIF, BMP, SVG, etc.)");
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

        // Thêm hierarchy path
        if (hierarchyPath != null && !hierarchyPath.isEmpty()) {
            targetDir = targetDir + "\\" + hierarchyPath;
        }

        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();

        // Tạo thư mục nếu chưa tồn tại
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Sensitive file saved to {} (hierarchy: {}): {}", category, hierarchyPath, targetPath);
        } catch (IOException e) {
            log.error("Could not store sensitive file {}: {}", originalFilename, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về relative path (không phải public URL)
        // VD: "IdCard/123/456/abc.jpg" hoặc "BusinessLicense/123/456/xyz.jpg"
        String categoryName = category == FileCategory.ID_CARD ? "IdCard" : "BusinessLicense";
        String relativePath = categoryName + "/" + (hierarchyPath != null && !hierarchyPath.isEmpty() ? hierarchyPath + "/" : "") + filename;

        log.info("Sensitive file relative path: {}", relativePath);
        return relativePath;
    }

    /**
     * Lấy base directory (từ env hoặc fallback)
     * Dùng cho FileAccessTokenService để validate path
     */
    public String getBaseDirectory() {
        return baseDir;
    }

    /**
     * Lưu file với cấu trúc phân cấp theo ownership hierarchy
     * VD: ProductImage/user_id/shop_id/product_id
     * 
     * @param file File cần lưu
     * @param category Loại file
     * @param hierarchyPath Cấu trúc thư mục phân cấp (VD: "123/456/789" cho user_id/shop_id/product_id)
     * @return URL tương đối có thể truy cập từ frontend
     */
    public String storeFileWithHierarchy(MultipartFile file, FileCategory category, String hierarchyPath) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // FILE_MESSAGE handled separately
        if (category == FileCategory.FILE_MESSAGE) {
            throw new IllegalArgumentException("Use storeChatFile() for FILE_MESSAGE category");
        }

        // Validate file type - chỉ chấp nhận ảnh
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Chỉ chấp nhận file ảnh (JPG, JPEG, PNG, WEBP, GIF, BMP, SVG, etc.)");
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

        // Chọn thư mục base dựa trên category
        String targetDir = getDirectoryByCategory(category);

        // Thêm hierarchy path nếu có
        if (hierarchyPath != null && !hierarchyPath.isEmpty()) {
            targetDir = targetDir + "\\" + hierarchyPath;
        }

        Path targetFolder = Paths.get(targetDir).toAbsolutePath().normalize();

        // Tạo thư mục nếu chưa tồn tại
        Files.createDirectories(targetFolder);

        Path targetPath = targetFolder.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved successfully to {} (hierarchy: {}): {}", category, hierarchyPath, targetPath);
        } catch (IOException e) {
            log.error("Could not store file {} to {}: {}", originalFilename, category, e.getMessage());
            throw new IOException("Không thể lưu file " + originalFilename, e);
        }

        // Trả về URL tương đối thay vì đường dẫn tuyệt đối
        String relativePath = convertToRelativeUrl(targetPath.toString());
        log.info("File URL for frontend: {}", relativePath);
        return relativePath;
    }

    /**
     * Lưu nhiều file với cấu trúc phân cấp
     * 
     * @param files Mảng các file cần lưu
     * @param category Loại file
     * @param hierarchyPath Cấu trúc thư mục phân cấp
     * @return Danh sách URL tương đối
     */
    public List<String> storeFilesWithHierarchy(MultipartFile[] files, FileCategory category, String hierarchyPath) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storeFileWithHierarchy(file, category, hierarchyPath);
                paths.add(url);
            }
        }
        return paths;
    }

    /**
     * Xóa nhiều file cùng lúc (dùng khi update)
     * @param filePathsOrUrls Danh sách đường dẫn hoặc URL cần xóa
     */
    public void deleteFiles(List<String> filePathsOrUrls) {
        if (filePathsOrUrls == null || filePathsOrUrls.isEmpty()) {
            return;
        }

        for (String filePathOrUrl : filePathsOrUrls) {
            deleteFile(filePathOrUrl);
        }
    }
}
