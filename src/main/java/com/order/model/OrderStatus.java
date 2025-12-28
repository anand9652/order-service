package com.order.model;

/**
 * Enum representing the lifecycle states of an Order.
 * Defines the valid transitions between order states.
 */
public enum OrderStatus {
    PENDING("Pending", "Order created, awaiting payment"),
    CONFIRMED("Confirmed", "Payment received, order confirmed"),
    PROCESSING("Processing", "Order is being processed"),
    SHIPPED("Shipped", "Order has been shipped"),
    DELIVERED("Delivered", "Order delivered to customer"),
    CANCELLED("Cancelled", "Order cancelled by customer or system"),
    FAILED("Failed", "Order processing failed");

    private final String displayName;
    private final String description;

    OrderStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Validates if a transition from current state to next state is allowed.
     *
     * @param nextStatus the target status
     * @return true if transition is allowed, false otherwise
     */
    public boolean isValidTransition(OrderStatus nextStatus) {
        switch (this) {
            case PENDING:
                return nextStatus == CONFIRMED || nextStatus == CANCELLED || nextStatus == FAILED;
            case CONFIRMED:
                return nextStatus == PROCESSING || nextStatus == CANCELLED;
            case PROCESSING:
                return nextStatus == SHIPPED || nextStatus == CANCELLED;
            case SHIPPED:
                return nextStatus == DELIVERED;
            case DELIVERED:
            case CANCELLED:
            case FAILED:
                return false; // Terminal states
            default:
                return false;
        }
    }

    /**
     * Checks if this status is a terminal state (no further transitions allowed).
     *
     * @return true if terminal, false otherwise
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }
}
