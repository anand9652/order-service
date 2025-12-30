package com.order.service;

import com.order.exception.InvalidTransitionException;
import com.order.exception.OrderNotFoundException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.OrderRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Business logic service for order processing and state transitions.
 * 
 * Features:
 * - Type-safe order operations with Optional-based null safety
 * - State machine validation for order transitions
 * - Automatic timestamp tracking via Order model
 * - Stream-based query operations for reporting
 * - ATOMIC state transitions with per-order locking to prevent concurrent transition races
 */
public class OrderService {
    private final OrderRepository repository;
    
    /**
     * Per-order locks to ensure atomic state transitions.
     * Each order ID gets its own lock object to synchronize concurrent transition attempts
     * on that specific order. This prevents race conditions where multiple threads
     * could attempt simultaneous state transitions on the same order.
     */
    private final Map<Long, Object> orderLocks = new ConcurrentHashMap<>();

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new order in the system.
     * Automatically initializes with PENDING status and current timestamps.
     */
    public Order createOrder(Order order) {
        return repository.save(order);
    }

    /**
     * Retrieves an order by ID using Optional for null-safe access.
     * 
     * @param id the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    public Order getOrder(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Deletes an order using Optional to verify existence first.
     * 
     * @param id the order ID
     * @throws OrderNotFoundException if order not found
     */
    public void deleteOrder(Long id) {
        repository.findById(id)
            .ifPresentOrElse(
                order -> repository.deleteById(id),
                () -> { throw new OrderNotFoundException(id); }
            );
    }

    /**
     * Transitions an order to a new status with validation.
     * ATOMIC OPERATION: The validation and state change happen as a single atomic unit,
     * using per-order locking via an orderLocks map. This prevents race conditions where
     * multiple threads could attempt simultaneous transitions on the same order.
     *
     * Pattern: Per-Object Locking (Condition Variable Pattern)
     * When multiple threads attempt concurrent transitions on the same order:
     * - Thread A acquires lock for orderId, retrieves order, validates and transitions successfully
     * - Thread B waits for same lock, then retrieves (now modified) order, sees invalid transition, throws exception
     * Result: Deterministic, only one thread succeeds per transition attempt.
     *
     * @param orderId the ID of the order
     * @param newStatus the target status
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order transitionOrder(Long orderId, OrderStatus newStatus) {
        // Get or create a lock for this specific order ID
        // computeIfAbsent ensures the lock is created atomically if not present
        Object lock = orderLocks.computeIfAbsent(orderId, id -> new Object());

        // ATOMIC BLOCK: Synchronize on the per-order lock
        // All threads attempting to transition this order ID will serialize through this lock
        synchronized (lock) {
            // Retrieve the current state of the order
            Order order = repository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

            // Validate transition - double-checked at atomic boundary
            // Another thread may have transitioned this order while we were waiting for the lock
            if (!order.getStatus().isValidTransition(newStatus)) {
                throw new InvalidTransitionException(orderId, order.getStatus(), newStatus);
            }

            // Update status and persist atomically
            order.setStatus(newStatus);
            return repository.save(order);
        }
    }

    /**
     * Pays an order (transitions from CREATED to PAID).
     */
    public Order payOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.PAID);
    }

    /**
     * Ships an order (transitions to SHIPPED).
     */
    public Order shipOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.SHIPPED);
    }

    /**
     * Delivers an order (transitions to DELIVERED).
     */
    public Order deliverOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.DELIVERED);
    }

    /**
     * Cancels an order (transitions to CANCELLED from any non-terminal state).
     */
    public Order cancelOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.CANCELLED);
    }

    // Stream-based utility methods for reporting and analytics

    /**
     * Retrieves all orders currently in the system.
     * 
     * @return list of all orders
     */
    public List<Order> getAllOrders() {
        return repository.findAll();
    }

    /**
     * Retrieves all orders with a specific status using stream filtering.
     * 
     * @param status the status to filter by
     * @return list of orders with the specified status
     */
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return repository.findAll().stream()
            .filter(order -> order.getStatus() == status)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all orders in terminal states using stream filtering.
     * Terminal states: DELIVERED, CANCELLED, FAILED
     * 
     * @return list of completed orders
     */
    public List<Order> getCompletedOrders() {
        return repository.findAll().stream()
            .filter(Order::isTerminalState)
            .collect(Collectors.toList());
    }

    /**
     * Calculates total value of all orders by status using stream aggregation.
     * 
     * @param status the status to aggregate
     * @return sum of order totals for the specified status
     */
    public double getTotalByStatus(OrderStatus status) {
        return repository.findAll().stream()
            .filter(order -> order.getStatus() == status)
            .mapToDouble(Order::getTotal)
            .sum();
    }

    /**
     * Counts orders by status using stream aggregation.
     * 
     * @param status the status to count
     * @return number of orders with the specified status
     */
    public long countByStatus(OrderStatus status) {
        return repository.findAll().stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }
}
