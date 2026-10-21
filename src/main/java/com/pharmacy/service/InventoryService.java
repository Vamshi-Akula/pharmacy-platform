package com.pharmacy.service;

import com.pharmacy.domain.Drug;
import com.pharmacy.domain.OrderItem;
import com.pharmacy.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * All stock changes are single-document conditional updates ($inc guarded by a filter). MongoDB applies each
 * atomically, so two concurrent orders can never both take the last unit and stock can never go negative,
 * without needing multi-document transactions (which would require a replica set).
 */
@Service
public class InventoryService {
    private final MongoTemplate mongo;

    public InventoryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Drug get(String drugId) {
        Drug d = mongo.findById(drugId, Drug.class);
        if (d == null) {
            throw ApiException.notFound("Drug not found: " + drugId);
        }
        return d;
    }

    /** Reserves every item or none: on the first shortfall, already-reserved items are released. */
    public void reserveAll(List<OrderItem> items) {
        List<OrderItem> reserved = new ArrayList<>();
        for (OrderItem item : items) {
            boolean ok = mongo.updateFirst(
                    idQuery(item.drugId(), expr(availableAtLeast(item.quantity()))),
                    new Update().inc("reservedQuantity", item.quantity()).set("updatedAt", Instant.now()),
                    Drug.class).getModifiedCount() == 1;
            if (!ok) {
                releaseAll(reserved);
                Drug d = mongo.findById(item.drugId(), Drug.class);
                throw ApiException.conflict(d == null
                        ? "Drug not found: " + item.drugId()
                        : "Insufficient stock for '%s': requested %d, available %d"
                                .formatted(d.name(), item.quantity(), Math.max(d.availableQuantity(), 0)));
            }
            reserved.add(item);
        }
    }

    public void releaseAll(List<OrderItem> items) {
        for (OrderItem item : items) {
            mongo.updateFirst(
                    idQuery(item.drugId(), Criteria.where("reservedQuantity").gte(item.quantity())),
                    new Update().inc("reservedQuantity", -item.quantity()).set("updatedAt", Instant.now()),
                    Drug.class);
        }
    }

    /** Dispensing turns a reservation into a real stock deduction. */
    public void commitAll(List<OrderItem> items) {
        for (OrderItem item : items) {
            mongo.updateFirst(
                    idQuery(item.drugId(), Criteria.where("reservedQuantity").gte(item.quantity())
                            .and("stockQuantity").gte(item.quantity())),
                    new Update().inc("reservedQuantity", -item.quantity()).inc("stockQuantity", -item.quantity())
                            .set("updatedAt", Instant.now()),
                    Drug.class);
        }
    }

    /** Manual adjustment; refuses to push on-hand stock below what is already reserved (or below zero). */
    public Drug adjustStock(String drugId, int delta) {
        if (delta == 0) {
            throw ApiException.badRequest("delta must not be zero");
        }
        Query q = idQuery(drugId, expr(stockAfterDeltaCoversReserved(delta)));
        boolean ok = mongo.updateFirst(q,
                new Update().inc("stockQuantity", delta).set("updatedAt", Instant.now()), Drug.class)
                .getModifiedCount() == 1;
        Drug d = get(drugId);
        if (!ok) {
            throw ApiException.conflict("Cannot adjust stock by %d: on hand %d, reserved %d"
                    .formatted(delta, d.stockQuantity(), d.reservedQuantity()));
        }
        return d;
    }

    private static Query idQuery(String drugId, Criteria extra) {
        return new Query(new Criteria().andOperator(Criteria.where("_id").is(drugId), extra));
    }

    private static Criteria expr(AggregationExpression e) {
        return Criteria.expr(e);
    }

    private static AggregationExpression availableAtLeast(int qty) {
        return ctx -> new Document("$gte", List.of(
                new Document("$subtract", List.of("$stockQuantity", "$reservedQuantity")), qty));
    }

    private static AggregationExpression stockAfterDeltaCoversReserved(int delta) {
        return ctx -> new Document("$gte", List.of(
                new Document("$add", List.of("$stockQuantity", delta)), "$reservedQuantity"));
    }

    static AggregationExpression availableLessThanThreshold() {
        return ctx -> new Document("$lt", List.of(
                new Document("$subtract", List.of("$stockQuantity", "$reservedQuantity")), "$lowStockThreshold"));
    }
}
