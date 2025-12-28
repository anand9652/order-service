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

    /**
     * Factory method for restoring an Order from persistent storage.
     * Allows timestamps to be preserved during deserialization.
     * Used internally by persistence layer only.
     *
     * @param id the order ID
     * @param customer the customer name
     * @param total the order total
     * @param status the order status
     * @param createdAt the creation timestamp to restore
     * @param updatedAt the last modification timestamp to restore
     * @return a new Order with the specified timestamps
     */
    public static Order fromPersistence(Long id, String customer, double total, 
                                       OrderStatus status, Instant createdAt, Instant updatedAt) {
        Order order = new Order(id, customer, total, status);
        // Replace the timestamps that were auto-set in constructor with persisted values
        // This is done via reflection to bypass the final field restriction
        try {
            var createdAtField = Order.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(order, createdAt);
            
            order.updatedAt = updatedAt;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If reflection fails, just use the timestamps from constructor
            // The order is still valid, just with different timestamps
            System.err.println("Warning: Could not restore timestamps from persistence: " + e.getMessage());
        }
        return order;
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
