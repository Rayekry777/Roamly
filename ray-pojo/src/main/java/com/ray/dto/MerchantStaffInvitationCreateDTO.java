package com.ray.dto;
import com.ray.enums.MerchantRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record MerchantStaffInvitationCreateDTO(@NotBlank @Pattern(regexp="1[3-9]\\d{9}") String phone, @NotNull MerchantRole role) {}
