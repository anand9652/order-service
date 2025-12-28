package com.order.exception;

import com.order.model.OrderStatus;

/**
 * Exception thrown when an invalid order state transition is attempted.
 * Provides detailed information about the attempted and invalid transition.
 */
public class InvalidTransitionException extends RuntimeException {
    private final Long orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    /**
     * Constructs an InvalidTransitionException with order and status details.
     *
     * @param orderId the ID of the order
     * @param currentStatus the current status of the order
     * @param requestedStatus the requested target status
     */
    public InvalidTransitionException(Long orderId, OrderStatus currentStatus, OrderStatus requestedStatus) {
        super(String.format(
            "Invalid state transition for Order %d: cannot transition from %s to %s",
            orderId, currentStatus.getDisplayName(), requestedStatus.getDisplayName()
        ));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    /**
     * Constructs an InvalidTransitionException with a custom message.
     *
     * @param message detailed error message
     */
    public InvalidTransitionException(String message) {
        super(message);
        this.orderId = null;
        this.currentStatus = null;
        this.requestedStatus = null;
    }

    /**
     * Constructs an InvalidTransitionException with message and cause.
     *
     * @param message detailed error message
     * @param cause the underlying cause
     */
    public InvalidTransitionException(String message, Throwable cause) {
        super(message, cause);
        this.orderId = null;
        this.currentStatus = null;
        this.requestedStatus = null;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getRequestedStatus() {
        return requestedStatus;
    }

    /**
     * Returns a detailed error message with transition information.
     *
     * @return formatted error message
     */
    public String getDetailedMessage() {
        if (orderId != null && currentStatus != null && requestedStatus != null) {
            return String.format(
                "Order ID: %d\nCurrent Status: %s (%s)\nRequested Status: %s (%s)\nReason: This transition is not allowed by the order state machine",
                orderId,
                currentStatus.getDisplayName(), currentStatus.getDescription(),
                requestedStatus.getDisplayName(), requestedStatus.getDescription()
            );
        }
        return getMessage();
    }
}
