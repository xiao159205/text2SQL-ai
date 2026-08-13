package com.xym.antifraud.service;

import com.xym.antifraud.config.StorageProperties;
import com.xym.antifraud.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceTest {

    @TempDir
    Path frontendAssertsDirectory;

    @Test
    void storesSupportedImageInConfiguredFrontendDirectory() throws Exception {
        AvatarStorageService service = new AvatarStorageService(
                new StorageProperties(frontendAssertsDirectory.toString()));

        AvatarStorageService.StoredAvatar avatar = service.store(new MockMultipartFile(
                "avatarFile", "avatar.png", "image/png", new byte[]{1, 2, 3}));

        assertThat(avatar.publicPath()).startsWith("src/asserts/").endsWith(".png");
        assertThat(Files.readAllBytes(frontendAssertsDirectory.resolve(avatar.filename())))
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsNonImageUpload() {
        AvatarStorageService service = new AvatarStorageService(
                new StorageProperties(frontendAssertsDirectory.toString()));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "avatarFile", "script.js", "application/javascript", new byte[]{1})))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("头像仅支持");
    }
}
