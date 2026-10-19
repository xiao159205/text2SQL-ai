package com.xym.antifraud.service;

import com.xym.antifraud.dto.admin.AdminUserRequest;
import com.xym.antifraud.dto.admin.AdminUserResponse;
import com.xym.antifraud.entity.User;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

@Service
public class AdminUserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorageService;

    public AdminUserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                            AvatarStorageService avatarStorageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.avatarStorageService = avatarStorageService;
    }

    public List<AdminUserResponse> list(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return userMapper.selectAll(normalized).stream().map(this::toResponse).toList();
    }

    public AdminUserResponse get(Long id) {
        return toResponse(requireUser(id));
    }

    @Transactional
    public AdminUserResponse create(AdminUserRequest request) {
        ValidatedInput input = validate(request, true);
        checkDuplicates(input.username(), input.phone(), null);
        AvatarStorageService.StoredAvatar avatar = avatarStorageService.store(request.getAvatarFile());
        try {
            User user = new User();
            user.setUsername(input.username());
            user.setPhone(input.phone());
            user.setPassword(passwordEncoder.encode(input.password()));
            user.setAvatarPath(avatar.publicPath());
            user.setRole(input.role());
            userMapper.insert(user);
            replaceDataSources(user.getId(), input.dataSources());
            return toResponse(requireUser(user.getId()));
        } catch (DuplicateKeyException exception) {
            avatarStorageService.deleteQuietly(avatar);
            throw conflict();
        } catch (RuntimeException exception) {
            avatarStorageService.deleteQuietly(avatar);
            throw exception;
        }
    }

    @Transactional
    public AdminUserResponse update(Long operatorId, Long id, AdminUserRequest request) {
        User current = requireUser(id);
        ValidatedInput input = validate(request, false);
        checkDuplicates(input.username(), input.phone(), id);
        if (operatorId.equals(id) && !"ADMIN".equals(input.role())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_ROLE_CHANGE",
                    "不能取消自己的管理员权限");
        }
        if ("ADMIN".equals(normalizeRole(current.getRole()))
                && "USER".equals(input.role()) && userMapper.countAdmins() <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAST_ADMIN",
                    "系统至少需要保留一名管理员");
        }

        AvatarStorageService.StoredAvatar newAvatar = null;
        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {
            newAvatar = avatarStorageService.store(request.getAvatarFile());
        }
        String oldAvatarPath = current.getAvatarPath();
        try {
            current.setUsername(input.username());
            current.setPhone(input.phone());
            if (input.password() != null) {
                current.setPassword(passwordEncoder.encode(input.password()));
            }
            if (newAvatar != null) current.setAvatarPath(newAvatar.publicPath());
            current.setRole(input.role());
            userMapper.update(current);
            replaceDataSources(id, input.dataSources());
        } catch (DuplicateKeyException exception) {
            avatarStorageService.deleteQuietly(newAvatar);
            throw conflict();
        } catch (RuntimeException exception) {
            avatarStorageService.deleteQuietly(newAvatar);
            throw exception;
        }
        if (newAvatar != null) avatarStorageService.deletePublicPathQuietly(oldAvatarPath);
        return toResponse(requireUser(id));
    }

    @Transactional
    public void delete(Long operatorId, Long id) {
        User target = requireUser(id);
        if (operatorId.equals(id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_DELETE",
                    "不能删除当前登录的管理员账号");
        }
        if ("ADMIN".equals(normalizeRole(target.getRole())) && userMapper.countAdmins() <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAST_ADMIN",
                    "系统至少需要保留一名管理员");
        }
        userMapper.deleteById(id);
        avatarStorageService.deletePublicPathQuietly(target.getAvatarPath());
    }

    private User requireUser(Long id) {
        User user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private void checkDuplicates(String username, String phone, Long excludedId) {
        boolean phoneExists = excludedId == null
                ? userMapper.countByPhone(phone) > 0
                : userMapper.countByPhoneExcludingId(phone, excludedId) > 0;
        boolean usernameExists = excludedId == null
                ? userMapper.countByUsername(username) > 0
                : userMapper.countByUsernameExcludingId(username, excludedId) > 0;
        if (phoneExists) throw new BusinessException(HttpStatus.CONFLICT, "PHONE_EXISTS", "该手机号已注册");
        if (usernameExists) throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该用户名已存在");
    }

    private ValidatedInput validate(AdminUserRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        String password = request.getPassword() == null || request.getPassword().isBlank()
                ? null : request.getPassword();
        String role = normalizeRole(request.getRole());
        if (username.isEmpty() || username.length() > 64) throw new IllegalArgumentException("用户名长度应为 1-64 个字符");
        if (!PHONE_PATTERN.matcher(phone).matches()) throw new IllegalArgumentException("请输入正确的 11 位手机号");
        if (creating && password == null) throw new IllegalArgumentException("新增用户必须设置密码");
        if (password != null && password.length() < 6) throw new IllegalArgumentException("密码至少 6 位");
        if (creating && (request.getAvatarFile() == null || request.getAvatarFile().isEmpty())) {
            throw new IllegalArgumentException("新增用户必须上传头像");
        }
        return new ValidatedInput(username, phone, password, role,
                normalizeDataSources(request.getDataSources(), role));
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(normalized) && !"USER".equals(normalized)) {
            throw new IllegalArgumentException("角色只能是 ADMIN 或 USER");
        }
        return normalized;
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getPhone(),
                user.getAvatarPath(), normalizeRole(user.getRole()), dataSourcesFor(user),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private List<String> normalizeDataSources(List<String> values, String role) {
        if ("ADMIN".equals(role)) return List.of("mysql", "hive");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.forEach(value -> normalized.add(
                    com.xym.antifraud.datasource.DatabaseType.from(value).name().toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(normalized);
    }

    private void replaceDataSources(Long userId, List<String> dataSources) {
        userMapper.deleteDataSources(userId);
        if (!dataSources.isEmpty()) userMapper.insertDataSources(userId, dataSources);
    }

    private List<String> dataSourcesFor(User user) {
        return "ADMIN".equals(normalizeRole(user.getRole()))
                ? List.of("mysql", "hive")
                : userMapper.selectDataSources(user.getId());
    }

    private BusinessException conflict() {
        return new BusinessException(HttpStatus.CONFLICT, "USER_EXISTS", "手机号或用户名已存在");
    }

    private record ValidatedInput(String username, String phone, String password, String role,
                                  List<String> dataSources) {}
}
