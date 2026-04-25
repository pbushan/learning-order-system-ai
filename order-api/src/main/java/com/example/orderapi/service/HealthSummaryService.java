package com.example.orderapi.service;

import com.example.orderapi.dto.HealthSummaryResponse;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HealthSummaryService {

    private static final Logger log = LoggerFactory.getLogger(HealthSummaryService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public HealthSummaryService(OrderRepository orderRepository,
                                ProductRepository productRepository,
                                CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    public HealthSummaryResponse getSummary() {
        try {
            return new HealthSummaryResponse(
                    orderRepository.count(),
                    productRepository.count(),
                    customerRepository.count()
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to load health summary counts; returning zeros", ex);
            return new HealthSummaryResponse(0L, 0L, 0L);
        }
    }
}
