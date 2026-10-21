package com.pharmacy.domain;

import java.time.Instant;

public record StatusChange(OrderStatus status, String note, Instant at) {
}
