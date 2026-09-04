package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "商户入驻完整草稿快照")
public record MerchantApplicationSaveDTO(
        @NotNull @Min(0) @Max(Integer.MAX_VALUE) @Schema(description = "当前乐观锁版本，首次保存为0") Integer version,
        @Size(max = 128) @Schema(description = "门店名称") String shopName,
        @Size(max = 64) @Schema(description = "统一社会信用代码") String licenseNumber,
        @Size(max = 64) @Schema(description = "法定代表人") String legalRepresentative,
        @Size(max = 64) @Schema(description = "联系人") String contactName,
        @Pattern(regexp = "1[3-9]\\d{9}", message = "contactPhone 格式无效") @Schema(description = "联系人手机号") String contactPhone,
        @Pattern(regexp = "[1-9]\\d*", message = "shopTypeId 必须是正整数字符串") @Schema(type = "string", description = "门店类目 ID") String shopTypeId,
        @Size(max = 16) @Schema(description = "城市编码") String cityCode,
        @Size(max = 64) @Schema(description = "区县") String district,
        @Size(max = 255) @Schema(description = "详细地址") String address,
        @Digits(integer = 3, fraction = 6) @DecimalMin("-180") @DecimalMax("180") @Schema(description = "经度") BigDecimal longitude,
        @Digits(integer = 2, fraction = 6) @DecimalMin("-90") @DecimalMax("90") @Schema(description = "纬度") BigDecimal latitude,
        @Size(max = 7) @Valid @Schema(description = "结构化营业时间") List<BusinessDayHoursDTO> businessHours,
        @Pattern(regexp = "[1-9]\\d*", message = "licenseMediaId 必须是正整数字符串") @Schema(type = "string", description = "营业执照媒体 ID") String licenseMediaId,
        @Size(max = 9) @Schema(description = "有序经营图片 ID") List<@Pattern(regexp = "[1-9]\\d*", message = "经营图片 ID 必须是正整数字符串") String> galleryMediaIds,
        @Size(max = 64) @Schema(description = "Mock 结算户名") String settlementAccountName,
        @Size(max = 64) @Schema(description = "Mock 结算银行") String settlementBankName,
        @Pattern(regexp = "\\d{4}", message = "settlementAccountSuffix 必须为4位数字") @Schema(description = "Mock 结算账号后四位") String settlementAccountSuffix) {
    public MerchantApplicationSaveDTO {
        businessHours = businessHours == null ? null : List.copyOf(businessHours);
        galleryMediaIds = galleryMediaIds == null ? List.of() : List.copyOf(galleryMediaIds);
    }
}
