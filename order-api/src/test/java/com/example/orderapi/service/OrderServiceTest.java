package com.example.orderapi.service;

import com.example.orderapi.domain.Customer;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.dto.ShippingDecision;
import com.example.orderapi.messaging.OrderSubmittedEvent;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LambdaShippingService lambdaShippingService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void submit_shouldInvokeLambda_updateOrder_andPublishEvent() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setEmail("priya@example.com");
        customer.setName("Priya Sharma");

        Order order = new Order();
        order.setId(10L);
        order.setCustomer(customer);
        order.setProductName("Teddy Bear");
        order.setQuantity(2);
        order.setTotalAmount(new BigDecimal("75.00"));
        order.setStatus(OrderStatus.DRAFT);

        ShippingDecision shippingDecision = new ShippingDecision();
        shippingDecision.setShippingType("PRIORITY");
        shippingDecision.setEstimatedDeliveryDays(2);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(lambdaShippingService.decideShipping(10L, "Teddy Bear", 2, "75.00"))
                .thenReturn(shippingDecision);
        when(orderRepository.save(order)).thenReturn(order);

        Order saved = orderService.submit(10L);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(saved.getShippingType()).isEqualTo("PRIORITY");
        assertThat(saved.getEstimatedDeliveryDays()).isEqualTo(2);

        ArgumentCaptor<OrderSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(OrderSubmittedEvent.class);
        verify(orderEventPublisher).publish(eventCaptor.capture());

        OrderSubmittedEvent event = eventCaptor.getValue();
        assertThat(event.getOrderId()).isEqualTo(10L);
        assertThat(event.getCustomerId()).isEqualTo(1L);
        assertThat(event.getShippingType()).isEqualTo("PRIORITY");
    }
}
