package com.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** End-to-end flow against an embedded MongoDB (downloaded on first run). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"app.seed.enabled=false", "de.flapdoodle.mongodb.embedded.version=7.0.14"})
class OrderFlowTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    private JsonNode call(String method, String url, Object body, HttpStatus expected) throws Exception {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        ResponseEntity<String> r = http.exchange(url, HttpMethod.valueOf(method),
                body == null ? null : new HttpEntity<>(body), String.class);
        assertThat(r.getStatusCode()).as(url + " -> " + r.getBody()).isEqualTo(expected);
        return json.readTree(r.getBody() == null ? "null" : r.getBody());
    }

    private String drug(String name, boolean rx, int stock, int threshold) throws Exception {
        return call("POST", "/drugs", Map.of("name", name, "genericName", name, "dosageForm", "Tablet",
                "strength", "10mg", "category", "Test", "price", 2.5, "prescriptionRequired", rx,
                "stockQuantity", stock, "lowStockThreshold", threshold), HttpStatus.CREATED).get("id").asText();
    }

    private JsonNode inv(String id) throws Exception {
        return call("GET", "/inventory/" + id, null, HttpStatus.OK);
    }

    private String order(String drugId, int qty) throws Exception {
        return call("POST", "/prescription-orders", Map.of("patientId", "p1",
                "items", List.of(Map.of("drugId", drugId, "quantity", qty))), HttpStatus.CREATED)
                .get("id").asText();
    }

    private String status(String orderId) throws Exception {
        return call("GET", "/prescription-orders/" + orderId, null, HttpStatus.OK).get("order").get("status").asText();
    }

    @Test
    void fullLifecycle() throws Exception {
        String rx = drug("RxDrug", true, 10, 5);
        String otc = drug("OtcDrug", false, 10, 5);

        String o1 = order(rx, 4);
        assertThat(inv(rx).get("reservedQuantity").asInt()).isEqualTo(4);
        assertThat(inv(rx).get("availableQuantity").asInt()).isEqualTo(6);
        assertThat(inv(rx).get("lowStock").asBoolean()).isFalse();
        String o1b = order(rx, 2);
        assertThat(inv(rx).get("lowStock").asBoolean()).isTrue();
        assertThat(call("GET", "/drugs/low-stock", null, HttpStatus.OK).toString()).contains(rx);
        assertThat(call("GET", "/prescription-orders/" + o1, null, HttpStatus.OK).get("order").get("total").asDouble())
                .isEqualTo(10.0);

        // all-or-nothing reservation
        call("POST", "/prescription-orders", Map.of("patientId", "p1", "items", List.of(
                Map.of("drugId", otc, "quantity", 3), Map.of("drugId", rx, "quantity", 5))), HttpStatus.CONFLICT);
        assertThat(inv(otc).get("reservedQuantity").asInt()).isZero();
        assertThat(inv(rx).get("reservedQuantity").asInt()).isEqualTo(6);

        call("PATCH", "/prescription-orders/" + o1b + "/cancel", Map.of("reason", "x"), HttpStatus.OK);
        assertThat(inv(rx).get("reservedQuantity").asInt()).isEqualTo(4);
        call("PATCH", "/prescription-orders/" + o1b + "/cancel", null, HttpStatus.CONFLICT);
        assertThat(inv(rx).get("reservedQuantity").asInt()).isEqualTo(4);

        JsonNode pay = call("POST", "/payments", Map.of("orderId", o1), HttpStatus.CREATED);
        assertThat(pay.get("status").asText()).isEqualTo("SUCCESS");
        call("POST", "/payments", Map.of("orderId", o1), HttpStatus.CONFLICT);
        assertThat(status(o1)).isEqualTo("PENDING_VERIFICATION");
        call("PATCH", "/prescription-orders/" + o1 + "/dispense", null, HttpStatus.CONFLICT);
        call("PATCH", "/prescription-orders/" + o1 + "/verify",
                Map.of("pharmacistId", "ph1", "approved", true), HttpStatus.OK);
        call("PATCH", "/prescription-orders/" + o1 + "/dispense", null, HttpStatus.OK);
        assertThat(inv(rx).get("stockQuantity").asInt()).isEqualTo(6);
        assertThat(inv(rx).get("reservedQuantity").asInt()).isZero();
        call("PATCH", "/prescription-orders/" + o1 + "/cancel", null, HttpStatus.CONFLICT);

        String o2 = order(otc, 3);
        call("POST", "/payments", Map.of("orderId", o2, "outcome", "FAILURE"), HttpStatus.CREATED);
        assertThat(status(o2)).isEqualTo("FAILED");
        assertThat(inv(otc).get("reservedQuantity").asInt()).isZero();
        assertThat(call("GET", "/payments/order/" + o2, null, HttpStatus.OK)).hasSize(1);

        String o3 = order(otc, 2);
        call("POST", "/payments", Map.of("orderId", o3), HttpStatus.CREATED);
        assertThat(status(o3)).isEqualTo("VERIFIED");

        String o4 = order(rx, 1);
        call("POST", "/payments", Map.of("orderId", o4), HttpStatus.CREATED);
        call("PATCH", "/prescription-orders/" + o4 + "/verify",
                Map.of("pharmacistId", "ph1", "approved", false), HttpStatus.OK);
        assertThat(inv(rx).get("reservedQuantity").asInt()).isZero();

        assertThat(call("GET", "/prescription-orders?status=cancelled", null, HttpStatus.OK)).hasSize(2);
        assertThat(call("GET", "/prescription-orders/patient/p1", null, HttpStatus.OK)).hasSize(5);
        assertThat(call("GET", "/drugs?search=rxdr", null, HttpStatus.OK)).hasSize(1);
        assertThat(call("GET", "/drugs?category=test", null, HttpStatus.OK)).hasSize(2);
        call("PATCH", "/drugs/" + rx + "/stock", Map.of("delta", 20), HttpStatus.OK);
        call("PATCH", "/drugs/" + rx + "/stock", Map.of("delta", -100), HttpStatus.CONFLICT);
        call("GET", "/prescription-orders?status=bogus", null, HttpStatus.BAD_REQUEST);
        call("POST", "/drugs", Map.of("name", ""), HttpStatus.BAD_REQUEST);
    }
}
