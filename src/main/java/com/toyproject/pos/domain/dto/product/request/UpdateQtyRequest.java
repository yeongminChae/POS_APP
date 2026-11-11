package com.toyproject.pos.domain.dto.product.request;

import jakarta.validation.constraints.Min;

public record UpdateQtyRequest(@Min(1) int qty) {
}
