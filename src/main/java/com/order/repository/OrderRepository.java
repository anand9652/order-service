package com.order.repository;

import java.util.List;
import java.util.Optional;

import com.order.model.Order;

/**
 * Repository interface for Order persistence operations.
 * 
 * Provides CRUD operations and bulk retrieval for stream-based processing.
 */
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    void deleteById(Long id);
    List<Order> findAll();
}
