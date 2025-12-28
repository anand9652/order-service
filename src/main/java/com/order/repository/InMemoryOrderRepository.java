package com.order.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.order.model.Order;

/**
 * Thread-safe in-memory implementation of OrderRepository.
 * 
 * Uses ConcurrentHashMap for concurrent reads/writes and AtomicLong for thread-safe ID generation.
 * Suitable for testing and demonstration purposes.
 */
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(idSeq.getAndIncrement());
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }
}
