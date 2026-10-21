package com.pharmacy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document("payments")
public record Payment(
        @Id String id,
        @Indexed String orderId,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount,
        PaymentStatus status,
        String method,
        String failureReason,
        Instant createdAt) {
}
