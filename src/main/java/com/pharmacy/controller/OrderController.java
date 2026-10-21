package com.pharmacy.controller;

import com.pharmacy.domain.PrescriptionOrder;
import com.pharmacy.dto.OrderDtos.CancelRequest;
import com.pharmacy.dto.OrderDtos.CreateOrderRequest;
import com.pharmacy.dto.OrderDtos.OrderDetails;
import com.pharmacy.dto.OrderDtos.VerifyRequest;
import com.pharmacy.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prescription-orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionOrder submit(@Valid @RequestBody CreateOrderRequest req) {
        return orders.submit(req);
    }

    @GetMapping("/{id}")
    public OrderDetails get(@PathVariable String id) {
        return orders.details(id);
    }

    @GetMapping("/patient/{patientId}")
    public List<PrescriptionOrder> byPatient(@PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return orders.byPatient(patientId, page, size);
    }

    @GetMapping
    public List<PrescriptionOrder> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return orders.list(status, page, size);
    }

    @PatchMapping("/{id}/cancel")
    public PrescriptionOrder cancel(@PathVariable String id, @RequestBody(required = false) CancelRequest req) {
        return orders.cancel(id, req);
    }

    @PatchMapping("/{id}/verify")
    public PrescriptionOrder verify(@PathVariable String id, @Valid @RequestBody VerifyRequest req) {
        return orders.verify(id, req);
    }

    @PatchMapping("/{id}/dispense")
    public PrescriptionOrder dispense(@PathVariable String id) {
        return orders.dispense(id);
    }
}
