package com.example.orderapi.service;

import com.example.orderapi.domain.Product;
import com.example.orderapi.dto.ProductRequest;
import com.example.orderapi.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest request;

    @BeforeEach
    void setUp() {
        request = new ProductRequest();
        request.setSku("WM-12345");
        request.setName("Wireless Mouse");
        request.setDescription("Ergonomic Bluetooth mouse");
        request.setCategory("Electronics");
        ProductRequest.PriceRequest price = new ProductRequest.PriceRequest();
        price.setCurrency("USD");
        price.setAmount(new BigDecimal("29.99"));
        request.setPrice(price);

        ProductRequest.PhysicalRequest physical = new ProductRequest.PhysicalRequest();
        ProductRequest.WeightRequest weight = new ProductRequest.WeightRequest();
        weight.setValue(new BigDecimal("0.2"));
        weight.setUnit("kg");
        physical.setWeight(weight);
        ProductRequest.DimensionsRequest dimensions = new ProductRequest.DimensionsRequest();
        dimensions.setLength(new BigDecimal("10"));
        dimensions.setWidth(new BigDecimal("6"));
        dimensions.setHeight(new BigDecimal("4"));
        dimensions.setUnit("cm");
        physical.setDimensions(dimensions);
        request.setPhysical(physical);

        ProductRequest.ShippingRequest shipping = new ProductRequest.ShippingRequest();
        shipping.setFragile(false);
        shipping.setHazmat(false);
        shipping.setRequiresCooling(false);
        shipping.setMaxStackable(10);
        request.setShipping(shipping);

        ProductRequest.StatusRequest status = new ProductRequest.StatusRequest();
        status.setActive(true);
        status.setShippable(true);
        request.setStatus(status);

        request.setTags(List.of("mouse", "wifi"));
    }

    @Test
    void create_shouldSaveProductWithNormalizedFields() {
        when(productRepository.existsBySku("WM-12345")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        Product result = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();
        assertThat(saved.getSku()).isEqualTo("WM-12345");
        assertThat(saved.getPrice().getCurrency()).isEqualTo("USD");
        assertThat(saved.getTags()).containsExactly("mouse", "wifi");
        assertThat(result.getPhysical().getWeight().getUnit()).isEqualTo("kg");
    }

    @Test
    void create_shouldThrowWhenSkuAlreadyExists() {
        when(productRepository.existsBySku("WM-12345")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU already exists");
    }

}
