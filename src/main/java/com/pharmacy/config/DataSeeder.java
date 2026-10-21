package com.pharmacy.config;

import com.pharmacy.domain.Drug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Inserts a small sample catalog on first start so the API can be demoed immediately. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {
    private final MongoTemplate mongo;

    public DataSeeder(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mongo.count(new org.springframework.data.mongodb.core.query.Query(), Drug.class) > 0) {
            return;
        }
        Instant now = Instant.now();
        mongo.insertAll(List.of(
                drug("Paracetamol 500", "Paracetamol", "Tablet", "500mg", "Pain Relief", "3.50", false, 200, 20, now),
                drug("Ibuprofen 200", "Ibuprofen", "Tablet", "200mg", "Pain Relief", "5.25", false, 150, 20, now),
                drug("Cetirizine 10", "Cetirizine", "Tablet", "10mg", "Allergy", "4.00", false, 8, 10, now),
                drug("Amoxicillin 500", "Amoxicillin", "Capsule", "500mg", "Antibiotic", "12.75", true, 100, 15, now),
                drug("Atorvastatin 20", "Atorvastatin", "Tablet", "20mg", "Cardiovascular", "18.40", true, 60, 10, now),
                drug("Salbutamol Inhaler", "Salbutamol", "Inhaler", "100mcg", "Respiratory", "9.90", true, 5, 5, now)));
    }

    private static Drug drug(String name, String generic, String form, String strength, String category,
            String price, boolean rx, int stock, int threshold, Instant now) {
        return new Drug(null, name, generic, form, strength, category, new BigDecimal(price), rx, stock, 0,
                threshold, now, now);
    }
}
