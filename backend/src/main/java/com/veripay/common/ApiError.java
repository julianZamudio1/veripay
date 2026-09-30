package com.veripay.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String codigo, String mensaje, Map<String, String> campos) {

    public static ApiError of(int status, String codigo, String mensaje) {
        return new ApiError(Instant.now(), status, codigo, mensaje, Map.of());
    }
}
