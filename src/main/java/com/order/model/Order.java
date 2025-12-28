package com.order.model;

import java.util.Objects;

public class Order {
    private Long id;
    private String customer;
    private double total;
    private OrderStatus status;

    public Order() {
        this.status = OrderStatus.PENDING;
    }

    public Order(Long id, String customer, double total) {
        this.id = id;
        this.customer = customer;
        this.total = total;
        this.status = OrderStatus.PENDING;
    }

    public Order(Long id, String customer, double total, OrderStatus status) {
        this.id = id;
        this.customer = customer;
        this.total = total;
        this.status = status != null ? status : OrderStatus.PENDING;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    /**
     * Transitions order to a new status if the transition is valid.
     *
     * @param newStatus the target status
     * @return true if transition succeeded, false otherwise
     */
    public boolean transitionTo(OrderStatus newStatus) {
        if (status.isValidTransition(newStatus)) {
            this.status = newStatus;
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customer='" + customer + "', total=" + total + ", status=" + status + "}";
    }
}
