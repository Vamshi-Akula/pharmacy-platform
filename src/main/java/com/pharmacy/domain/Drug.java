package com.pharmacy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * stockQuantity is physical on-hand stock; reservedQuantity is the part of it held by open orders.
 * Available = stockQuantity - reservedQuantity (never stored, so it can't drift).
 */
@Document("drugs")
public record Drug(
        @Id String id,
        @Indexed String name,
        String genericName,
        String dosageForm,
        String strength,
        @Indexed String category,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal price,
        boolean prescriptionRequired,
        int stockQuantity,
        int reservedQuantity,
        int lowStockThreshold,
        Instant createdAt,
        Instant updatedAt) {

    public int availableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    public boolean lowStock() {
        return availableQuantity() < lowStockThreshold;
    }
}
