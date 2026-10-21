package com.pharmacy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Items and status history are embedded: an order is always read and written as one unit. */
@Document("prescription_orders")
@CompoundIndex(name = "patient_created", def = "{'patientId': 1, 'createdAt': -1}")
public record PrescriptionOrder(
        @Id String id,
        String patientId,
        List<OrderItem> items,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal total,
        @Indexed OrderStatus status,
        boolean verificationRequired,
        Verification verification,
        String cancellationReason,
        List<StatusChange> history,
        Instant createdAt,
        Instant updatedAt) {
}
