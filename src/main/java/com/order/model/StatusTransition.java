package com.order.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a single order status transition.
 *
 * Purpose:
 * - Records when an order transitioned to a specific status
 * - Maintains complete audit trail of order lifecycle
 * - Enables historical queries and state tracking
 *
 * Thread Safety:
 * - All fields are immutable (final)
 * - Instant is thread-safe
 * - No setters, only constructor initialization
 */
public class StatusTransition {
    private final OrderStatus status;
    private final Instant timestamp;

    /**
     * Creates a new status transition record.
     *
     * @param status the order status after transition
     * @param timestamp the time of transition
     */
    public StatusTransition(OrderStatus status, Instant timestamp) {
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }

    /**
     * Gets the status at this transition point.
     *
     * @return the OrderStatus
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Gets the timestamp when this transition occurred.
     *
     * @return the Instant
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusTransition that = (StatusTransition) o;
        return status == that.status && timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, timestamp);
    }

    @Override
    public String toString() {
        return String.format("%s at %s", status.getDisplayName(), timestamp);
    }
}
