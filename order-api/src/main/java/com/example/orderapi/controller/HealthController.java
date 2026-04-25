package com.example.orderapi.controller;

import com.example.orderapi.dto.HealthSummaryResponse;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/health-summary")
public class HealthController {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final String serviceName;

    public HealthController(CustomerRepository customerRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository,
                            @Value("${spring.application.name:order-api}") String serviceName) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.serviceName = serviceName;
    }

    @GetMapping
    public HealthSummaryResponse healthSummary() {
        return new HealthSummaryResponse(
                serviceName,
                Instant.now(),
                safeCount(customerRepository),
                safeCount(productRepository),
                safeCount(orderRepository)
        );
    }

    private long safeCount(org.springframework.data.repository.CrudRepository<?, ?> repository) {
        try {
            return repository != null ? Math.max(0L, repository.count()) : 0L;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
