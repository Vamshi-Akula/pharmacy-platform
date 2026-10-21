package com.pharmacy.domain;

import java.time.Instant;

public record Verification(String pharmacistId, boolean approved, String notes, Instant at) {
}
