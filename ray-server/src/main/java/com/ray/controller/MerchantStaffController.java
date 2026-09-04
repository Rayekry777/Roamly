package com.ray.controller;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.MerchantStaffService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/merchant") @SecurityRequirement(name="BearerAuth") @Tag(name="商户员工")
public class MerchantStaffController { private final MerchantStaffService service; public MerchantStaffController(MerchantStaffService service){this.service=service;}
 @GetMapping("/staff") @Operation(summary="查询员工",operationId="listMerchantStaff") public Result<PageResult<MerchantStaffVO>> list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return Result.ok(service.list(page,size));}
 @PostMapping("/staff-invitations") @Operation(summary="邀请员工",operationId="createMerchantStaffInvitation") public Result<MerchantStaffInvitationVO> invite(@Valid @RequestBody MerchantStaffInvitationCreateDTO req,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.invite(req,key));}
 @PostMapping("/staff-invitations/{id}/revocation") @Operation(summary="撤销员工邀请",operationId="revokeMerchantStaffInvitation") public Result<MerchantStaffInvitationVO> revoke(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.revoke(IdUtils.parse(id,"invitationId"),key));}
 @PostMapping("/staff-invitations/acceptance") @Operation(summary="接受员工邀请",operationId="acceptMerchantStaffInvitation") public Result<MerchantStaffVO> accept(@Valid @RequestBody MerchantStaffAcceptanceDTO req,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.accept(req,key));}
 @PostMapping("/staff/{id}/disablement") @Operation(summary="停用员工",operationId="disableMerchantStaff") public Result<MerchantStaffVO> disable(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.setEnabled(IdUtils.parse(id,"staffId"),false,key));}
 @PostMapping("/staff/{id}/activation") @Operation(summary="启用员工",operationId="activateMerchantStaff") public Result<MerchantStaffVO> activate(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.setEnabled(IdUtils.parse(id,"staffId"),true,key));}
}
