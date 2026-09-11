package com.ray.controller;

import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.MerchantStaffService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 暴露租户员工管理与游客接受短时邀请的接口。 */
@Validated
@RestController
@RequestMapping("/v1/merchant")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户员工")
public class MerchantStaffController {
    private final MerchantStaffService service;

    public MerchantStaffController(MerchantStaffService service) {
        this.service = service;
    }

    /** 查询当前租户公司的店长与核销员。 */
    @GetMapping("/staff")
    @Operation(summary = "查询员工", operationId = "listMerchantStaff")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "403",
                    description = "当前账号不是激活租户",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))))
    public Result<PageResult<MerchantStaffVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(page, size));
    }

    /** 验证已注册游客手机号并签发 60 秒邀请凭证。 */
    @PostMapping("/staff-invitations")
    @Operation(summary = "签发员工邀请凭证", operationId = "createMerchantStaffInvitation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "邀请凭证已签发", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "403",
                description = "当前账号不是激活租户",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "目标手机号尚未注册",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "目标账号、有效邀请或幂等键冲突",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "503",
                description = "邀请凭证缓存不可用",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantStaffInvitationVO> invite(
            @Valid @RequestBody MerchantStaffInvitationCreateDTO request,
            @Parameter(description = "8 至 128 位签发幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.invite(request, idempotencyKey));
    }

    /** 撤销当前租户公司尚未消费的邀请。 */
    @PostMapping("/staff-invitations/{id}/revocation")
    @Operation(summary = "撤销员工邀请", operationId = "revokeMerchantStaffInvitation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "邀请已撤销", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "403",
                description = "当前账号不是激活租户",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "邀请不存在",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "409",
                description = "邀请状态已变化",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantStaffInvitationVO> revoke(
            @PathVariable String id,
            @Parameter(description = "8 至 128 位撤销幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.revoke(IdUtils.parse(id, "invitationId"), idempotencyKey));
    }

    /** 由当前手机号登录的纯游客接受六位邀请凭证。 */
    @PostMapping("/staff-invitations/acceptance")
    @Operation(summary = "接受员工邀请", operationId = "acceptMerchantStaffInvitation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "已加入公司", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "409",
                description = "账号资格、公司归属或并发状态冲突",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "429",
                description = "60 秒内错误尝试次数过多",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "503",
                description = "邀请限流服务不可用",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantStaffVO> accept(
            @Valid @RequestBody MerchantStaffAcceptanceDTO request,
            @Parameter(description = "8 至 128 位接受幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.accept(request, idempotencyKey));
    }

    /** 停用当前租户公司中的员工。 */
    @PostMapping("/staff/{id}/disablement")
    @Operation(summary = "停用员工", operationId = "disableMerchantStaff")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "员工已停用", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "403",
                description = "当前账号不是激活租户",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "员工不存在",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantStaffVO> disable(
            @PathVariable String id,
            @Parameter(description = "8 至 128 位停用幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.setEnabled(IdUtils.parse(id, "staffId"), false, idempotencyKey));
    }

    /** 启用当前租户公司中由员工管理停用的员工。 */
    @PostMapping("/staff/{id}/activation")
    @Operation(summary = "启用员工", operationId = "activateMerchantStaff")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "员工已启用", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "403",
                description = "当前账号不是激活租户",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "员工不存在",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantStaffVO> activate(
            @PathVariable String id,
            @Parameter(description = "8 至 128 位启用幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.setEnabled(IdUtils.parse(id, "staffId"), true, idempotencyKey));
    }
}
