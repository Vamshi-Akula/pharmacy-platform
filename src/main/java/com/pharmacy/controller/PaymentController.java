package com.pharmacy.controller;

import com.pharmacy.domain.Payment;
import com.pharmacy.dto.PaymentDtos.PaymentRequest;
import com.pharmacy.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment process(@Valid @RequestBody PaymentRequest req) {
        return payments.process(req);
    }

    @GetMapping("/order/{orderId}")
    public List<Payment> forOrder(@PathVariable String orderId) {
        return payments.forOrder(orderId);
    }
}
