package com.xym.antifraud.controller;

import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.service.PromptConfigService;
import com.xym.antifraud.service.UserSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Text2SQL 的 MySQL/Hive System Prompt 在线配置接口。 */
@RestController
@RequestMapping("/api/prompt")
public class PromptConfigController {

    private final PromptConfigService promptConfigService;
    private final UserSessionService userSessionService;

    public PromptConfigController(PromptConfigService promptConfigService,
                                  UserSessionService userSessionService) {
        this.promptConfigService = promptConfigService;
        this.userSessionService = userSessionService;
    }

    @GetMapping
    public PromptConfigService.PromptView get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "mysql") String database) {
        userSessionService.requireUserId(authorization);
        return promptConfigService.get(DatabaseType.from(database));
    }

    @PutMapping
    public PromptConfigService.PromptView save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SavePromptRequest request) {
        userSessionService.requireUserId(authorization);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return promptConfigService.save(DatabaseType.from(request.database()), request.content());
    }

    @PostMapping("/reset")
    public PromptConfigService.PromptView reset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "mysql") String database) {
        userSessionService.requireUserId(authorization);
        return promptConfigService.reset(DatabaseType.from(database));
    }

    public record SavePromptRequest(String database, String content) {
    }
}
