package com.ray.controller;

import com.ray.dto.MerchantAvatarUpdateDTO;
import com.ray.dto.MerchantNicknameUpdateDTO;
import com.ray.dto.MerchantPasswordChangeDTO;
import com.ray.dto.MerchantPhoneChangeDTO;
import com.ray.dto.MerchantPhoneSmsCodeDTO;
import com.ray.result.Result;
import com.ray.service.MerchantAccountService;
import com.ray.vo.MerchantAccountProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商户本人资料、头像、手机号和密码安全接口。 */
@RestController
@RequestMapping("/v1/merchant/account")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户账号资料")
public class MerchantAccountController {
    private final MerchantAccountService service;

    public MerchantAccountController(MerchantAccountService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    @Operation(summary = "查询商户本人资料", operationId = "getCurrentMerchantProfile")
    public Result<MerchantAccountProfileVO> profile() {
        return Result.ok(service.currentProfile());
    }

    @PutMapping("/nickname")
    @Operation(summary = "修改商户本人昵称", operationId = "updateCurrentMerchantNickname")
    public Result<MerchantAccountProfileVO> updateNickname(@Valid @RequestBody MerchantNicknameUpdateDTO request) {
        return Result.ok(service.updateNickname(request));
    }

    @PutMapping("/avatar")
    @Operation(summary = "修改商户本人头像", operationId = "updateCurrentMerchantAvatar")
    public Result<MerchantAccountProfileVO> updateAvatar(@Valid @RequestBody MerchantAvatarUpdateDTO request) {
        return Result.ok(service.updateAvatar(request));
    }

    @PostMapping("/phone-change/sms-codes")
    @Operation(summary = "发送商户新手机号换绑验证码", operationId = "sendCurrentMerchantPhoneChangeCode")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "验证码已发送"))
    public ResponseEntity<Void> sendPhoneChangeCode(@Valid @RequestBody MerchantPhoneSmsCodeDTO request) {
        service.sendPhoneChangeCode(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/phone")
    @Operation(summary = "换绑商户本人手机号", operationId = "changeCurrentMerchantPhone")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "手机号已换绑且全部商户会话已注销"))
    public ResponseEntity<Void> changePhone(@Valid @RequestBody MerchantPhoneChangeDTO request) {
        service.changePhone(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-change/sms-codes")
    @Operation(summary = "发送商户本人密码修改验证码", operationId = "sendCurrentMerchantPasswordChangeCode")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "验证码已发送"))
    public ResponseEntity<Void> sendPasswordChangeCode() {
        service.sendPasswordChangeCode();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    @Operation(summary = "修改商户本人密码", operationId = "changeCurrentMerchantPassword")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "密码已修改且全部商户会话已注销"))
    public ResponseEntity<Void> changePassword(@Valid @RequestBody MerchantPasswordChangeDTO request) {
        service.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
