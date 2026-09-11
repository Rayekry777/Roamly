package com.ray.enums;

/** 经营媒体用途及中文释义。 */
public enum BusinessMediaPurpose {
    LICENSE("营业执照"),
    GALLERY("经营图片"),
    VOUCHER_COVER("券封面"),
    VOUCHER_DETAIL("券详情图"),
    MERCHANT_AVATAR("商户头像");

    private final String label;

    BusinessMediaPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
