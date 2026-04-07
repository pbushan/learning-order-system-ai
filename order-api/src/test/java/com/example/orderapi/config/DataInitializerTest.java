package com.example.orderapi.config;

import com.example.orderapi.domain.Customer;
import com.example.orderapi.domain.Product;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    @SuppressWarnings("unchecked")
    void seedsWhenDatabaseEmpty() throws Exception {
        when(customerRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(customerRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        dataInitializer.run(null);

        ArgumentCaptor<List> customerCaptor = ArgumentCaptor.forClass(List.class);
        verify(customerRepository).saveAll(customerCaptor.capture());
        List<Customer> customers = (List<Customer>) customerCaptor.getValue();
        assertThat(customers).hasSize(10);
        assertThat(customers.get(0).getEmail()).isEqualTo("ava.thompson@example.com");

        ArgumentCaptor<List> productCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(productCaptor.capture());
        List<Product> products = (List<Product>) productCaptor.getValue();
        assertThat(products).hasSize(10);
        assertThat(products.get(0).getSku()).isEqualTo("WM-1001");
    }

    @Test
    void skipsWhenDataAlreadyPresent() throws Exception {
        when(customerRepository.count()).thenReturn(5L);
        when(productRepository.count()).thenReturn(7L);

        dataInitializer.run(null);

        verify(customerRepository, never()).saveAll(anyList());
        verify(productRepository, never()).saveAll(anyList());
    }
}
