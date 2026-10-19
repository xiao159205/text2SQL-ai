package com.xym.antifraud.controller;

import com.xym.antifraud.dto.admin.AdminUserRequest;
import com.xym.antifraud.dto.admin.AdminUserResponse;
import com.xym.antifraud.entity.User;
import com.xym.antifraud.service.AdminUserService;
import com.xym.antifraud.service.UserSessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserSessionService userSessionService;

    public AdminUserController(AdminUserService adminUserService,
                               UserSessionService userSessionService) {
        this.adminUserService = adminUserService;
        this.userSessionService = userSessionService;
    }

    @GetMapping
    public List<AdminUserResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword) {
        userSessionService.requireAdmin(authorization);
        return adminUserService.list(keyword);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        userSessionService.requireAdmin(authorization);
        return adminUserService.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminUserResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @ModelAttribute AdminUserRequest request) {
        userSessionService.requireAdmin(authorization);
        return adminUserService.create(request);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminUserResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @ModelAttribute AdminUserRequest request) {
        User operator = userSessionService.requireAdmin(authorization);
        return adminUserService.update(operator.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        User operator = userSessionService.requireAdmin(authorization);
        adminUserService.delete(operator.getId(), id);
        return Map.of("deleted", true, "id", id);
    }
}
