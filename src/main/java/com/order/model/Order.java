package com.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a customer order with state machine transitions.
 * 
 * Features:
 * - Automatic timestamp tracking (createdAt, updatedAt)
 * - State machine validation via OrderStatus enum
 * - Immutable creation metadata (id, customer, total, createdAt)
 * - Mutable operational state (status, updatedAt)
 */
public class Order {
    private Long id;
    private String customer;
    private double total;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Order() {
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Order(Long id, String customer, double total) {
        this(id, customer, total, OrderStatus.PENDING);
    }

    public Order(Long id, String customer, double total, OrderStatus status) {
        this.id = id;
        this.customer = customer;
        this.total = total;
        this.status = Objects.requireNonNullElse(status, OrderStatus.PENDING);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters with Optional support for null-safe operations
    public Long getId() { 
        return id; 
    }

    public void setId(Long id) { 
        this.id = id; 
    }

    public String getCustomer() { 
        return customer; 
    }

    public void setCustomer(String customer) { 
        this.customer = customer;
        this.updatedAt = Instant.now();
    }

    public double getTotal() { 
        return total; 
    }

    public void setTotal(double total) { 
        this.total = total;
        this.updatedAt = Instant.now();
    }

    public OrderStatus getStatus() { 
        return status; 
    }

    public void setStatus(OrderStatus status) { 
        this.status = Objects.requireNonNullElse(status, OrderStatus.PENDING);
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the timestamp when this order was created.
     * Immutable - set only once at instantiation.
     */
    public Instant getCreatedAt() { 
        return createdAt; 
    }

    /**
     * Returns the timestamp of the last modification to this order.
     * Updated automatically whenever status, customer, or total changes.
     */
    public Instant getUpdatedAt() { 
        return updatedAt; 
    }

    /**
     * Transitions order to a new status if the transition is valid.
     * Automatically updates the updatedAt timestamp on successful transition.
     *
     * @param newStatus the target status
     * @return true if transition succeeded, false otherwise
     */
    public boolean transitionTo(OrderStatus newStatus) {
        if (status.isValidTransition(newStatus)) {
            this.status = newStatus;
            this.updatedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Determines if this order is in a terminal state.
     * Uses Optional to safely check status state.
     *
     * @return true if the order cannot transition further
     */
    public boolean isTerminalState() {
        return Optional.of(status)
            .map(OrderStatus::isTerminal)
            .orElse(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() { 
        return Objects.hash(id); 
    }

    @Override
    public String toString() {
        return """
            Order{id=%d, customer='%s', total=%.2f, status=%s, \
            createdAt=%s, updatedAt=%s}""".formatted(
                id, customer, total, status, createdAt, updatedAt);
    }
}
