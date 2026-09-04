package com.ray.enums;

/** 商户申请审核决定及中文释义。 */
public enum MerchantApplicationReviewDecision {
    APPROVAL("通过"),
    REJECTION("驳回");

    private final String label;

    MerchantApplicationReviewDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
