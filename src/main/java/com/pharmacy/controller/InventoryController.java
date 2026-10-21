package com.pharmacy.controller;

import com.pharmacy.dto.DrugDtos.InventoryResponse;
import com.pharmacy.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/{drugId}")
    public InventoryResponse get(@PathVariable String drugId) {
        return InventoryResponse.from(inventory.get(drugId));
    }
}
