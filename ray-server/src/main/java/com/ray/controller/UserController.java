package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.UserInfoService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.SignStreakVO;
import com.ray.vo.UserInfoVO;
import com.ray.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "用户")
public class UserController {
    private final UserService userService;
    private final UserInfoService userInfoService;

    public UserController(UserService userService, UserInfoService userInfoService) {
        this.userService = userService;
        this.userInfoService = userInfoService;
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询当前用户", operationId = "getCurrentUser")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<UserVO> me() {
        return Result.ok(userService.getCurrentUser());
    }

    @GetMapping("/{userId}")
    @SecurityRequirements
    @Operation(summary = "查询用户摘要", operationId = "getUser")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<UserVO> user(@Parameter(description = "用户 ID") @PathVariable String userId) {
        return Result.ok(userService.getUser(IdUtils.parse(userId, "userId")));
    }

    @GetMapping("/{userId}/profile")
    @SecurityRequirements
    @Operation(summary = "查询用户资料", operationId = "getUserProfile")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<UserInfoVO> profile(@Parameter(description = "用户 ID") @PathVariable String userId) {
        return Result.ok(userInfoService.getProfile(IdUtils.parse(userId, "userId")));
    }

    @PutMapping("/me/check-ins/today")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "今日签到", operationId = "checkInToday")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "签到成功"))
    public ResponseEntity<Void> checkIn() {
        userService.sign();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/check-ins/streak")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询连续签到天数", operationId = "getCheckInStreak")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<SignStreakVO> streak() {
        return Result.ok(new SignStreakVO(userService.signStreak()));
    }
}
