package com.pharmacy.domain;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Price is snapshotted at order time so later catalog price changes never alter an existing order. */
public record OrderItem(
        String drugId,
        String drugName,
        boolean prescriptionRequired,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal unitPrice,
        int quantity,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal lineTotal) {
}
