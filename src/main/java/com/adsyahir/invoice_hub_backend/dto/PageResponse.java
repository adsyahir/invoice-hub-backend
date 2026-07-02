package com.adsyahir.invoice_hub_backend.dto;

import java.util.List;

/** Stable, minimal paginated envelope (avoids leaking Spring's PageImpl shape). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages) {
}
