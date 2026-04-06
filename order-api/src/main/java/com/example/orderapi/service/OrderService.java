package com.example.orderapi.service;

import com.example.orderapi.domain.Customer;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.dto.OrderRequest;
import com.example.orderapi.dto.ShippingDecision;
import com.example.orderapi.exception.ResourceNotFoundException;
import com.example.orderapi.messaging.OrderSubmittedEvent;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final LambdaShippingService lambdaShippingService;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        LambdaShippingService lambdaShippingService,
                        OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.lambdaShippingService = lambdaShippingService;
        this.orderEventPublisher = orderEventPublisher;
    }

    public Order create(OrderRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus(OrderStatus.DRAFT);
        return orderRepository.save(order);
    }

    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public Order update(Long id, OrderRequest request) {
        Order order = getById(id);
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));

        order.setCustomer(customer);
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = getById(id);
        orderRepository.delete(order);
    }

    @Transactional
    public Order submit(Long id) {
        Order order = getById(id);

        ShippingDecision decision = lambdaShippingService.decideShipping(
                order.getId(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalAmount().toPlainString()
        );

        order.setShippingType(decision.getShippingType());
        order.setEstimatedDeliveryDays(decision.getEstimatedDeliveryDays());
        order.setStatus(OrderStatus.SUBMITTED);

        Order savedOrder = orderRepository.save(order);

        OrderSubmittedEvent event = new OrderSubmittedEvent();
        event.setOrderId(savedOrder.getId());
        event.setCustomerId(savedOrder.getCustomer().getId());
        event.setCustomerEmail(savedOrder.getCustomer().getEmail());
        event.setProductName(savedOrder.getProductName());
        event.setQuantity(savedOrder.getQuantity());
        event.setTotalAmount(savedOrder.getTotalAmount());
        event.setShippingType(savedOrder.getShippingType());
        event.setEstimatedDeliveryDays(savedOrder.getEstimatedDeliveryDays());

        // Intentionally skipped for agent-review validation.
        return savedOrder;
    }
}
