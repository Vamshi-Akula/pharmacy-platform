package com.pharmacy.service;

import com.pharmacy.domain.Payment;
import com.pharmacy.domain.PaymentStatus;
import com.pharmacy.domain.PrescriptionOrder;
import com.pharmacy.dto.PaymentDtos.PaymentRequest;
import com.pharmacy.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final MongoTemplate mongo;
    private final OrderService orders;

    public PaymentService(MongoTemplate mongo, OrderService orders) {
        this.mongo = mongo;
        this.orders = orders;
    }

    /** Only an order still in SUBMITTED can be paid; the status transition in OrderService is the guard against double payment. */
    public Payment process(PaymentRequest req) {
        boolean success = parseOutcome(req.outcome());
        PrescriptionOrder order = orders.get(req.orderId());
        String failureReason = success ? null : "Simulated payment failure";

        orders.applyPaymentOutcome(order.id(), success, failureReason);

        return mongo.insert(new Payment(null, order.id(), order.total(),
                success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED,
                req.method() != null ? req.method() : "CARD", failureReason, Instant.now()));
    }

    public List<Payment> forOrder(String orderId) {
        orders.get(orderId);
        return orders.paymentsFor(orderId);
    }

    private static boolean parseOutcome(String outcome) {
        if (outcome == null || outcome.isBlank() || outcome.equalsIgnoreCase("SUCCESS")) {
            return true;
        }
        if (outcome.equalsIgnoreCase("FAILURE") || outcome.equalsIgnoreCase("FAILED")) {
            return false;
        }
        throw ApiException.badRequest("outcome must be SUCCESS or FAILURE");
    }
}
