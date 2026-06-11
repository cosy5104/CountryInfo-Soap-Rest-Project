package com.country.info.controller;

import com.country.info.model.Language;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * DTOs for the REST API layer.
 */
public class CountryDtos {

    // ── Inbound ────────────────────────────────────────────────────

    /** POST /api/countries  →  { "name": "Tanzania" } */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryNameRequest {
        @NotBlank(message = "Country name must not be blank")
        private String name;
    }

    /** PUT /api/countries/{id}  –  partial update payload */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountryUpdateRequest {
        private String name;
        private String isoCode;
        private String capitalCity;
        private String phoneCode;
        private String continentCode;
        private String currencyIsoCode;
        private String countryFlag;
        private List<LanguageDto> languages;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageDto {
        private String isoCode;
        private String name;
    }

    // ── Outbound ───────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> ok(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
