package com.xym.antifraud.dto.admin;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 管理员新增或编辑用户时使用的 multipart 表单。 */
public class AdminUserRequest {

    private String username;
    private String phone;
    private String password;
    private String role;
    private List<String> dataSources;
    private MultipartFile avatarFile;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<String> getDataSources() { return dataSources; }
    public void setDataSources(List<String> dataSources) { this.dataSources = dataSources; }
    public MultipartFile getAvatarFile() { return avatarFile; }
    public void setAvatarFile(MultipartFile avatarFile) { this.avatarFile = avatarFile; }
}
