package com.example.orderconsumer.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange orderExchange(@Value("${app.exchange-name}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue orderSubmittedQueue(@Value("${app.queue-name}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding orderBinding(Queue orderSubmittedQueue,
                                TopicExchange orderExchange,
                                @Value("${app.routing-key}") String routingKey) {
        return BindingBuilder.bind(orderSubmittedQueue).to(orderExchange).with(routingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        return factory;
    }
}
