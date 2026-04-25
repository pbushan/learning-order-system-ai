package com.example.orderapi.service;

import com.example.orderapi.dto.HealthSummaryResponse;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class HealthSummaryServiceTest {

    @Test
    void getSummaryMapsRepositoryCounts() {
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        ProductRepository productRepository = Mockito.mock(ProductRepository.class);
        CustomerRepository customerRepository = Mockito.mock(CustomerRepository.class);

        when(orderRepository.count()).thenReturn(7L);
        when(productRepository.count()).thenReturn(11L);
        when(customerRepository.count()).thenReturn(13L);

        HealthSummaryService service = new HealthSummaryService(orderRepository, productRepository, customerRepository);
        HealthSummaryResponse response = service.getSummary();

        assertEquals(7L, response.getTotalOrders());
        assertEquals(11L, response.getTotalProducts());
        assertEquals(13L, response.getTotalCustomers());
    }
}
