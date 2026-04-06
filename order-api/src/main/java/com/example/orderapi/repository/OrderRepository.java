package com.example.orderapi.repository;

import com.example.orderapi.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = "customer")
    List<Order> findAll();

    @Override
    @EntityGraph(attributePaths = "customer")
    Optional<Order> findById(Long id);
}
