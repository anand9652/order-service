package com.order.service;

import com.order.exception.InvalidTransitionException;
import com.order.exception.OrderNotFoundException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.OrderRepository;

public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(Order order) {
        return repository.save(order);
    }

    public Order getOrder(Long id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public void deleteOrder(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new OrderNotFoundException(id);
        }
        repository.deleteById(id);
    }

    /**
     * Transitions an order to a new status.
     *
     * @param orderId the ID of the order
     * @param newStatus the target status
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order transitionOrder(Long orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId); // throws OrderNotFoundException if not found

        if (!order.getStatus().isValidTransition(newStatus)) {
            throw new InvalidTransitionException(orderId, order.getStatus(), newStatus);
        }

        order.setStatus(newStatus);
        return repository.save(order);
    }

    /**
     * Confirms an order (transitions from PENDING to CONFIRMED).
     *
     * @param orderId the order ID
     * @return the confirmed order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if order is not in PENDING state
     */
    public Order confirmOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.CONFIRMED);
    }

    /**
     * Processes an order (transitions to PROCESSING).
     *
     * @param orderId the order ID
     * @return the processing order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order processOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.PROCESSING);
    }

    /**
     * Ships an order (transitions to SHIPPED).
     *
     * @param orderId the order ID
     * @return the shipped order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order shipOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.SHIPPED);
    }

    /**
     * Delivers an order (transitions to DELIVERED).
     *
     * @param orderId the order ID
     * @return the delivered order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order deliverOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.DELIVERED);
    }

    /**
     * Cancels an order (transitions to CANCELLED).
     *
     * @param orderId the order ID
     * @return the cancelled order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order cancelOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.CANCELLED);
    }

    /**
     * Marks an order as failed (transitions to FAILED).
     *
     * @param orderId the order ID
     * @return the failed order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidTransitionException if transition is invalid
     */
    public Order failOrder(Long orderId) {
        return transitionOrder(orderId, OrderStatus.FAILED);
    }
}
