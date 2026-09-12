package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "替换工单标签")
public record CustomerServiceTagUpdateDTO(
        @NotNull @Size(max = 10) List<String> tagIds) {
    public CustomerServiceTagUpdateDTO {
        tagIds = tagIds == null ? null : List.copyOf(tagIds);
    }
}
