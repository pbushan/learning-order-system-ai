package com.example.orderapi.service;

import com.example.orderapi.dto.HealthSummaryResponse;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class HealthSummaryService {

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
        return new HealthSummaryResponse(
                orderRepository.count(),
                productRepository.count(),
                customerRepository.count()
        );
    }
}
