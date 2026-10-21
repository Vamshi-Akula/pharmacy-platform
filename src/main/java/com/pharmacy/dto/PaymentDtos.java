package com.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    /**
     * Payment is simulated: "outcome" lets the caller force SUCCESS (default) or FAILURE so both
     * paths can be demonstrated. The amount is always taken from the order total, never from the client.
     */
    public record PaymentRequest(@NotBlank String orderId, String method, String outcome) {
    }
}
