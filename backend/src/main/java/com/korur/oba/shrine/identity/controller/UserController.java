package com.korur.oba.shrine.identity.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.constant.controller.UserControllerLogConstant;
import com.korur.oba.shrine.identity.constant.controller.UserControllerResponseConstant;
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.dto.response.UserListResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "Identity | User Management", description = "User management endpoints")
public class UserController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserController.class);

    private final UserQueryService userQueryService;
    private final LoggingService loggingService;

    @GetMapping
    @Operation(summary = "List the caller and the users who share an active organization with the caller")
    public ResponseEntity<CustomResponse<List<UserListResponse>>> getAllUsers(
            @Parameter(hidden = true) @GetUser User user,
            @Valid @ModelAttribute @ParameterObject UserQueryRequest query) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserControllerLogConstant.USER_LIST_REQUEST);

        ResponseEntity<CustomResponse<List<UserListResponse>>> response =
                ResponseUtil.ok(UserControllerResponseConstant.RESPONSE_USERS_RETRIEVED, userQueryService.getAllUsers(user.getId(), query));

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserControllerLogConstant.USER_LIST_REQUEST_COMPLETED);

        return response;
    }
}
