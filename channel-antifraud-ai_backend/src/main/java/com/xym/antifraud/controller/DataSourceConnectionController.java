package com.xym.antifraud.controller;

import com.xym.antifraud.dto.datasource.DataSourceConnectionRequest;
import com.xym.antifraud.dto.datasource.DataSourceConnectionResponse;
import com.xym.antifraud.dto.datasource.DataSourceTestResponse;
import com.xym.antifraud.service.UserDataSourceConnectionService;
import com.xym.antifraud.service.UserSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-source-connections")
public class DataSourceConnectionController {
    private final UserSessionService sessionService;
    private final UserDataSourceConnectionService connectionService;

    public DataSourceConnectionController(UserSessionService sessionService,
                                          UserDataSourceConnectionService connectionService) {
        this.sessionService = sessionService;
        this.connectionService = connectionService;
    }

    @PostMapping("/test")
    public DataSourceTestResponse test(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DataSourceConnectionRequest request) {
        sessionService.requireDataSourceAccess(authorization, request.databaseType());
        return connectionService.test(request);
    }

    @PostMapping
    public DataSourceConnectionResponse connect(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DataSourceConnectionRequest request) {
        Long userId = sessionService.requireDataSourceAccess(authorization, request.databaseType());
        return connectionService.connect(userId, request);
    }
}
