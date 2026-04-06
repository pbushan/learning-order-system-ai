package com.example.orderconsumer.service;

import com.example.orderconsumer.domain.FulfillmentRecord;
import com.example.orderconsumer.messaging.OrderSubmittedEvent;
import com.example.orderconsumer.repository.FulfillmentRecordRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderSubmittedConsumer {

    private final FulfillmentRecordRepository fulfillmentRecordRepository;
    private final String queueName;

    public OrderSubmittedConsumer(FulfillmentRecordRepository fulfillmentRecordRepository,
                                  @Value("${app.queue-name}") String queueName) {
        this.fulfillmentRecordRepository = fulfillmentRecordRepository;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "#{@environment.getProperty('app.queue-name')}")
    public void consume(OrderSubmittedEvent event) {
        FulfillmentRecord record = new FulfillmentRecord();
        record.setOrderId(event.getOrderId());
        record.setCustomerId(event.getCustomerId());
        record.setCustomerEmail(event.getCustomerEmail());
        record.setProductName(event.getProductName());
        record.setQuantity(event.getQuantity());
        record.setTotalAmount(event.getTotalAmount());
        record.setShippingType(event.getShippingType());
        record.setEstimatedDeliveryDays(event.getEstimatedDeliveryDays());
        record.setProcessingStatus("FULFILLMENT_CREATED");
        record.setProcessedAt(LocalDateTime.now());

        fulfillmentRecordRepository.save(record);

        System.out.println("Consumed message from queue " + queueName + " for order " + event.getOrderId());
    }
}
