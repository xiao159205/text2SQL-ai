package com.xym.antifraud.service;

import com.xym.antifraud.config.StorageProperties;
import com.xym.antifraud.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final String AVATAR_RELATIVE_DIRECTORY = "src/asserts/";
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    private final Path avatarDirectory;

    public AvatarStorageService(StorageProperties properties) {
        avatarDirectory = Path.of(properties.avatarDirectory()).toAbsolutePath().normalize();
    }

    public StoredAvatar store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AVATAR_REQUIRED", "请上传头像");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR",
                    "头像仅支持 JPG、PNG、GIF 或 WebP 图片");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = avatarDirectory.resolve(filename).normalize();
        ensureInsideStorage(target);
        try {
            Files.createDirectories(avatarDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredAvatar(AVATAR_RELATIVE_DIRECTORY + filename, filename);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_SAVE_FAILED",
                    "头像保存失败，请稍后重试");
        }
    }

    public void deleteQuietly(StoredAvatar avatar) {
        if (avatar == null) {
            return;
        }
        try {
            Path target = avatarDirectory.resolve(avatar.filename()).normalize();
            ensureInsideStorage(target);
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 数据库写入失败时尽力清理；清理失败不覆盖原始异常。
        }
    }

    private void ensureInsideStorage(Path target) {
        if (!target.startsWith(avatarDirectory)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "文件名不合法");
        }
    }

    public record StoredAvatar(String publicPath, String filename) {
    }
}
