package com.pharmacy.controller;

import com.pharmacy.domain.Drug;
import com.pharmacy.dto.DrugDtos.DrugRequest;
import com.pharmacy.dto.DrugDtos.DrugResponse;
import com.pharmacy.dto.DrugDtos.StockAdjustmentRequest;
import com.pharmacy.service.DrugService;
import com.pharmacy.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drugs")
public class DrugController {
    private final DrugService drugs;
    private final InventoryService inventory;

    public DrugController(DrugService drugs, InventoryService inventory) {
        this.drugs = drugs;
        this.inventory = inventory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DrugResponse create(@Valid @RequestBody DrugRequest req) {
        return DrugResponse.from(drugs.create(req));
    }

    @GetMapping("/low-stock")
    public List<DrugResponse> lowStock() {
        return drugs.lowStock().stream().map(DrugResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DrugResponse get(@PathVariable String id) {
        return DrugResponse.from(drugs.get(id));
    }

    @GetMapping
    public List<DrugResponse> search(@RequestParam(required = false) String category,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return drugs.search(category, name, search, page, size).stream().map(DrugResponse::from).toList();
    }

    @PutMapping("/{id}")
    public DrugResponse update(@PathVariable String id, @Valid @RequestBody DrugRequest req) {
        return DrugResponse.from(drugs.update(id, req));
    }

    @PatchMapping("/{id}/stock")
    public DrugResponse adjustStock(@PathVariable String id, @Valid @RequestBody StockAdjustmentRequest req) {
        Drug d = inventory.adjustStock(id, req.delta());
        return DrugResponse.from(d);
    }
}
