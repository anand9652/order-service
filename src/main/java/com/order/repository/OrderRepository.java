package com.order.repository;

import java.util.Optional;

import com.order.model.Order;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    void deleteById(Long id);
}
