package com.pharmacy.service;

import com.pharmacy.domain.Drug;
import com.pharmacy.domain.OrderItem;
import com.pharmacy.domain.OrderStatus;
import com.pharmacy.domain.Payment;
import com.pharmacy.domain.PrescriptionOrder;
import com.pharmacy.domain.StatusChange;
import com.pharmacy.domain.Verification;
import com.pharmacy.dto.OrderDtos.CancelRequest;
import com.pharmacy.dto.OrderDtos.CreateOrderRequest;
import com.pharmacy.dto.OrderDtos.OrderDetails;
import com.pharmacy.dto.OrderDtos.OrderLineRequest;
import com.pharmacy.dto.OrderDtos.VerifyRequest;
import com.pharmacy.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Every status change is one atomic findAndModify guarded by "status is currently one of ...". Whoever wins that
 * transition is the only caller that then releases/commits stock, so concurrent cancel/payment/verify
 * calls cannot double-release stock.
 */
@Service
public class OrderService {
    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.PENDING_VERIFICATION, OrderStatus.VERIFIED);

    private final MongoTemplate mongo;
    private final InventoryService inventory;

    public OrderService(MongoTemplate mongo, InventoryService inventory) {
        this.mongo = mongo;
        this.inventory = inventory;
    }

    public PrescriptionOrder submit(CreateOrderRequest req) {
        // Merge duplicate drug lines so each drug is reserved once.
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (OrderLineRequest l : req.items()) {
            wanted.merge(l.drugId(), l.quantity(), Integer::sum);
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean needsVerification = false;
        for (var e : wanted.entrySet()) {
            Drug d = mongo.findById(e.getKey(), Drug.class);
            if (d == null) {
                throw ApiException.notFound("Drug not found: " + e.getKey());
            }
            BigDecimal line = d.price().multiply(BigDecimal.valueOf(e.getValue()));
            items.add(new OrderItem(d.id(), d.name(), d.prescriptionRequired(), d.price(), e.getValue(), line));
            total = total.add(line);
            needsVerification |= d.prescriptionRequired();
        }

        inventory.reserveAll(items);

        Instant now = Instant.now();
        var order = new PrescriptionOrder(null, req.patientId().trim(), items, total, OrderStatus.SUBMITTED,
                needsVerification, null, null,
                List.of(new StatusChange(OrderStatus.SUBMITTED, "Order submitted, stock reserved", now)), now, now);
        try {
            return mongo.insert(order);
        } catch (RuntimeException ex) {
            inventory.releaseAll(items);
            throw ex;
        }
    }

    public PrescriptionOrder get(String id) {
        PrescriptionOrder o = mongo.findById(id, PrescriptionOrder.class);
        if (o == null) {
            throw ApiException.notFound("Order not found: " + id);
        }
        return o;
    }

    public OrderDetails details(String id) {
        return new OrderDetails(get(id), paymentsFor(id));
    }

    public List<Payment> paymentsFor(String orderId) {
        return mongo.find(new Query(Criteria.where("orderId").is(orderId)).with(Sort.by("createdAt")), Payment.class);
    }

    public List<PrescriptionOrder> byPatient(String patientId, int page, int size) {
        return mongo.find(new Query(Criteria.where("patientId").is(patientId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).with(page(page, size)), PrescriptionOrder.class);
    }

    public List<PrescriptionOrder> list(String status, int page, int size) {
        Query q = new Query();
        if (status != null && !status.isBlank()) {
            q.addCriteria(Criteria.where("status").is(parseStatus(status)));
        }
        return mongo.find(q.with(Sort.by(Sort.Direction.DESC, "createdAt")).with(page(page, size)),
                PrescriptionOrder.class);
    }

    public PrescriptionOrder cancel(String id, CancelRequest req) {
        String by = req != null && req.cancelledBy() != null ? req.cancelledBy() : "unknown";
        String reason = req != null && req.reason() != null ? req.reason() : "No reason given";
        PrescriptionOrder o = transition(id, CANCELLABLE, OrderStatus.CANCELLED,
                "Cancelled by " + by + ": " + reason, u -> u.set("cancellationReason", reason), "cancelled");
        inventory.releaseAll(o.items());
        return o;
    }

    public PrescriptionOrder verify(String id, VerifyRequest req) {
        Instant now = Instant.now();
        var verification = new Verification(req.pharmacistId(), req.approved(), req.notes(), now);
        Set<OrderStatus> from = EnumSet.of(OrderStatus.PENDING_VERIFICATION);
        if (req.approved()) {
            return transition(id, from, OrderStatus.VERIFIED, "Verified by " + req.pharmacistId(),
                    u -> u.set("verification", verification), "verified");
        }
        String reason = "Prescription rejected by pharmacist " + req.pharmacistId();
        PrescriptionOrder o = transition(id, from, OrderStatus.CANCELLED, reason,
                u -> u.set("verification", verification).set("cancellationReason", reason), "verified");
        inventory.releaseAll(o.items());
        return o;
    }

    public PrescriptionOrder dispense(String id) {
        PrescriptionOrder o = transition(id, EnumSet.of(OrderStatus.VERIFIED), OrderStatus.DISPENSED,
                "Dispensed, stock deducted", u -> { }, "dispensed");
        inventory.commitAll(o.items());
        return o;
    }

    /**
     * Called by PaymentService. Success moves the order forward (to verification if it contains
     * prescription-required drugs, otherwise straight to verified); failure fails the order and releases stock.
     */
    PrescriptionOrder applyPaymentOutcome(String id, boolean success, String failureReason) {
        PrescriptionOrder current = get(id);
        if (success) {
            OrderStatus next = current.verificationRequired() ? OrderStatus.PENDING_VERIFICATION : OrderStatus.VERIFIED;
            return transition(id, EnumSet.of(OrderStatus.SUBMITTED), next, "Payment succeeded", u -> { }, "paid");
        }
        PrescriptionOrder o = transition(id, EnumSet.of(OrderStatus.SUBMITTED), OrderStatus.FAILED,
                "Payment failed: " + failureReason, u -> u.set("cancellationReason", "Payment failed"), "paid");
        inventory.releaseAll(o.items());
        return o;
    }

    private PrescriptionOrder transition(String id, Set<OrderStatus> from, OrderStatus to, String note,
            Consumer<Update> extra, String action) {
        Instant now = Instant.now();
        Update u = new Update().set("status", to).set("updatedAt", now).push("history", new StatusChange(to, note, now));
        extra.accept(u);
        PrescriptionOrder updated = mongo.findAndModify(
                new Query(new Criteria().andOperator(Criteria.where("_id").is(id), Criteria.where("status").in(from))),
                u, FindAndModifyOptions.options().returnNew(true), PrescriptionOrder.class);
        if (updated != null) {
            return updated;
        }
        PrescriptionOrder cur = get(id);
        throw ApiException.conflict("Order %s is %s and cannot be %s (allowed from: %s)"
                .formatted(id, cur.status(), action, from));
    }

    private static OrderStatus parseStatus(String s) {
        try {
            return OrderStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status '%s'. Valid: %s".formatted(s, List.of(OrderStatus.values())));
        }
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }
}
