package com.example.orderapi.controller;

import com.example.orderapi.dto.OrderRequest;
import com.example.orderapi.dto.OrderResponse;
import com.example.orderapi.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @CrossOrigin(origins = "*")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody OrderRequest request) {
        return OrderResponse.from(orderService.create(request));
    }

    @GetMapping
    public List<OrderResponse> getAll() {
        return orderService.getAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @CrossOrigin(origins = "*")
    public OrderResponse getById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @PutMapping("/{id}")
    @CrossOrigin(origins = "*")
    public OrderResponse update(@PathVariable Long id, @RequestBody OrderRequest request) {
        return OrderResponse.from(orderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @CrossOrigin(origins = "*")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @CrossOrigin(origins = "*")
    public OrderResponse submit(@PathVariable Long id) {
        return OrderResponse.from(orderService.submit(id));
    }

    static String formatOrderStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "Status: Unknown";
        }
        String normalized = status.trim();
        if (normalized.regionMatches(true, 0, "Status:", 0, "Status:".length())) {
            return normalized;
        }
        return "Status: " + normalized;
    }
}
