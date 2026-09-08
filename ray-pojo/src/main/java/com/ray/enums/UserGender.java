package com.ray.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 消费者公开性别。 */
@Schema(description = "性别")
public enum UserGender {
    UNDISCLOSED("保密"),
    MALE("男"),
    FEMALE("女");

    private final String label;

    UserGender(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
