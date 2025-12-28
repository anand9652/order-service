package com.order.service;

import com.order.exception.InvalidTransitionException;
import com.order.exception.OrderNotFoundException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.OrderRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic service for order processing and state transitions.
 * 
 * Features:
 * - Type-safe order operations with Optional-based null safety
 * - State machine validation for order transitions
 * - Automatic timestamp tracking via Order model
 * - Stream-based query operations for reporting
 */
public class OrderService {
    private final OrderRepository repository;

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
     * Uses Optional chaining for clean error handling.
     *
     * @param orderId the ID of the order
     * @param newStatus the target status
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order transitionOrder(Long orderId, OrderStatus newStatus) {
        // Retrieve and validate order using Optional
        Order order = repository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Validate transition using Optional to safely chain
        if (!order.getStatus().isValidTransition(newStatus)) {
            throw new InvalidTransitionException(orderId, order.getStatus(), newStatus);
        }

        order.setStatus(newStatus);
        return repository.save(order);
    }

    /**
     * Confirms an order (transitions from PENDING to CONFIRMED).
     */
    public Order confirmOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.CONFIRMED);
    }

    /**
     * Processes an order (transitions to PROCESSING).
     */
    public Order processOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.PROCESSING);
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
     * Cancels an order (transitions to CANCELLED).
     */
    public Order cancelOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.CANCELLED);
    }

    /**
     * Marks an order as failed (transitions to FAILED).
     */
    public Order failOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.FAILED);
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
