package com.ray.controller;

import com.ray.dto.AvatarUpdateDTO;
import com.ray.dto.CityPreferenceUpdateDTO;
import com.ray.dto.NicknameUpdateDTO;
import com.ray.dto.PasswordChangeDTO;
import com.ray.dto.PhoneChangeDTO;
import com.ray.dto.PhoneSmsCodeDTO;
import com.ray.dto.UserProfileUpdateDTO;
import com.ray.result.Result;
import com.ray.service.UserAccountSecurityService;
import com.ray.service.UserProfileService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.SignStreakVO;
import com.ray.vo.CurrentUserProfileVO;
import com.ray.vo.PublicUserProfileVO;
import com.ray.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final UserProfileService profileService;
    private final UserAccountSecurityService securityService;

    public UserController(
            UserService userService,
            UserProfileService profileService,
            UserAccountSecurityService securityService) {
        this.userService = userService;
        this.profileService = profileService;
        this.securityService = securityService;
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
    public Result<PublicUserProfileVO> profile(@Parameter(description = "用户 ID") @PathVariable String userId) {
        return Result.ok(profileService.publicProfile(IdUtils.parse(userId, "userId")));
    }

    @GetMapping("/me/profile")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询本人个人资料", operationId = "getCurrentUserProfile")
    public Result<CurrentUserProfileVO> currentProfile() {
        return Result.ok(profileService.currentProfile());
    }

    @PutMapping("/me/profile")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新本人性别和生日", operationId = "updateCurrentUserProfile")
    public Result<CurrentUserProfileVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO request) {
        return Result.ok(profileService.updateProfile(request));
    }

    @PutMapping("/me/nickname")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新本人昵称", operationId = "updateCurrentUserNickname")
    public Result<CurrentUserProfileVO> updateNickname(@Valid @RequestBody NicknameUpdateDTO request) {
        return Result.ok(profileService.updateNickname(request));
    }

    @PutMapping("/me/avatar")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新本人头像", operationId = "updateCurrentUserAvatar")
    public Result<CurrentUserProfileVO> updateAvatar(@Valid @RequestBody AvatarUpdateDTO request) {
        return Result.ok(profileService.updateAvatar(request));
    }

    @PutMapping("/me/city-preference")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "同步内部城市偏好", operationId = "updateCurrentUserCityPreference")
    public ResponseEntity<Void> updateCityPreference(@Valid @RequestBody CityPreferenceUpdateDTO request) {
        profileService.updateCityPreference(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/phone-change/sms-codes")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发送新手机号换绑验证码", operationId = "sendCurrentUserPhoneChangeCode")
    public ResponseEntity<Void> sendPhoneChangeCode(@Valid @RequestBody PhoneSmsCodeDTO request) {
        securityService.sendPhoneChangeCode(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/phone")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "换绑本人手机号", operationId = "changeCurrentUserPhone")
    public ResponseEntity<Void> changePhone(@Valid @RequestBody PhoneChangeDTO request) {
        securityService.changePhone(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password-change/sms-codes")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发送本人密码修改验证码", operationId = "sendCurrentUserPasswordChangeCode")
    public ResponseEntity<Void> sendPasswordChangeCode() {
        securityService.sendPasswordChangeCode();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/password")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "修改本人密码", operationId = "changeCurrentUserPassword")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeDTO request) {
        securityService.changePassword(request);
        return ResponseEntity.noContent().build();
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
