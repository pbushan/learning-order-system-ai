package com.example.orderapi.service;

import com.example.orderapi.messaging.OrderSubmittedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final String routingKey;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${app.rabbit.exchange-name}") String exchangeName,
                               @Value("${app.rabbit.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
    }

    public void publish(OrderSubmittedEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
    }
}
