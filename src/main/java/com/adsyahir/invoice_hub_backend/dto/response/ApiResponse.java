package com.adsyahir.invoice_hub_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// dto/response/ApiResponse.java
@Getter
@Builder
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Meta meta;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Meta {
        private int page;
        private int size;
        private long total;
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }
}