package com.pharmacy.dto;

import com.pharmacy.domain.Drug;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class DrugDtos {
    private DrugDtos() {
    }

    /** Used for both create (POST) and full update (PUT). */
    public record DrugRequest(
            @NotBlank String name,
            @NotBlank String genericName,
            @NotBlank String dosageForm,
            @NotBlank String strength,
            @NotBlank String category,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal price,
            @NotNull Boolean prescriptionRequired,
            @NotNull @Min(0) Integer stockQuantity,
            @Min(0) Integer lowStockThreshold) {
    }

    /** delta > 0 restocks, delta < 0 removes stock (e.g. damaged/expired). */
    public record StockAdjustmentRequest(@NotNull Integer delta, String reason) {
    }

    public record DrugResponse(
            String id,
            String name,
            String genericName,
            String dosageForm,
            String strength,
            String category,
            BigDecimal price,
            boolean prescriptionRequired,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            int lowStockThreshold,
            boolean lowStock,
            Instant createdAt,
            Instant updatedAt) {

        public static DrugResponse from(Drug d) {
            return new DrugResponse(d.id(), d.name(), d.genericName(), d.dosageForm(), d.strength(), d.category(),
                    d.price(), d.prescriptionRequired(), d.stockQuantity(), d.reservedQuantity(),
                    d.availableQuantity(), d.lowStockThreshold(), d.lowStock(), d.createdAt(), d.updatedAt());
        }
    }

    public record InventoryResponse(
            String drugId,
            String name,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            int lowStockThreshold,
            boolean lowStock) {

        public static InventoryResponse from(Drug d) {
            return new InventoryResponse(d.id(), d.name(), d.stockQuantity(), d.reservedQuantity(),
                    d.availableQuantity(), d.lowStockThreshold(), d.lowStock());
        }
    }
}
