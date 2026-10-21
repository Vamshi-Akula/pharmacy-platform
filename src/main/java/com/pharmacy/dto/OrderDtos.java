package com.pharmacy.dto;

import com.pharmacy.domain.Payment;
import com.pharmacy.domain.PrescriptionOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record OrderLineRequest(@NotBlank String drugId, @NotNull @Min(1) Integer quantity) {
    }

    public record CreateOrderRequest(
            @NotBlank String patientId,
            @NotEmpty List<@Valid @NotNull OrderLineRequest> items) {
    }

    /** approved=false rejects the prescription: the order is cancelled and its stock released. */
    public record VerifyRequest(@NotBlank String pharmacistId, @NotNull Boolean approved, String notes) {
    }

    public record CancelRequest(String cancelledBy, String reason) {
    }

    public record DispenseRequest(String pharmacistId) {
    }

    public record OrderDetails(PrescriptionOrder order, List<Payment> payments) {
    }
}
