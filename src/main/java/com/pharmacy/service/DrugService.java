package com.pharmacy.service;

import com.pharmacy.domain.Drug;
import com.pharmacy.exception.ApiException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import static com.pharmacy.service.InventoryService.availableLessThanThreshold;

@Service
public class DrugService {
    static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private final MongoTemplate mongo;

    public DrugService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Drug create(com.pharmacy.dto.DrugDtos.DrugRequest r) {
        var now = java.time.Instant.now();
        var drug = new Drug(null, r.name().trim(), r.genericName().trim(), r.dosageForm().trim(), r.strength().trim(),
                r.category().trim(), r.price(), r.prescriptionRequired(), r.stockQuantity(), 0,
                r.lowStockThreshold() != null ? r.lowStockThreshold() : DEFAULT_LOW_STOCK_THRESHOLD, now, now);
        return mongo.insert(drug);
    }

    public Drug get(String id) {
        Drug d = mongo.findById(id, Drug.class);
        if (d == null) {
            throw ApiException.notFound("Drug not found: " + id);
        }
        return d;
    }

    /** category is an exact (case-insensitive) match; name and search are case-insensitive substring matches. */
    public List<Drug> search(String category, String name, String search, int page, int size) {
        List<Criteria> filters = new java.util.ArrayList<>();
        if (hasText(category)) {
            filters.add(Criteria.where("category").regex("^" + Pattern.quote(category.trim()) + "$", "i"));
        }
        if (hasText(name)) {
            filters.add(Criteria.where("name").regex(Pattern.quote(name.trim()), "i"));
        }
        if (hasText(search)) {
            String q = Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("name").regex(q, "i"),
                    Criteria.where("genericName").regex(q, "i"),
                    Criteria.where("category").regex(q, "i")));
        }
        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }
        query.with(Sort.by("name")).with(PageRequest.of(Math.max(page, 0), clamp(size)));
        return mongo.find(query, Drug.class);
    }

    public List<Drug> lowStock() {
        Query query = new Query(Criteria.expr(availableLessThanThreshold())).with(Sort.by("name"));
        return mongo.find(query, Drug.class);
    }

    public Drug update(String id, com.pharmacy.dto.DrugDtos.DrugRequest r) {
        org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update()
                .set("name", r.name().trim())
                .set("genericName", r.genericName().trim())
                .set("dosageForm", r.dosageForm().trim())
                .set("strength", r.strength().trim())
                .set("category", r.category().trim())
                .set("price", r.price())
                .set("prescriptionRequired", r.prescriptionRequired())
                .set("updatedAt", java.time.Instant.now());
        if (r.lowStockThreshold() != null) {
            u.set("lowStockThreshold", r.lowStockThreshold());
        }
        // Stock and reserved quantities are deliberately not editable here; they change only via inventory operations.
        Drug updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(id)), u,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), Drug.class);
        if (updated == null) {
            throw ApiException.notFound("Drug not found: " + id);
        }
        return updated;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
