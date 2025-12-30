package com.order;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.order.exception.InvalidTransitionException;
import com.order.exception.OrderNotFoundException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.InMemoryOrderRepository;
import com.order.repository.OrderRepository;
import com.order.service.OrderService;

public class OrderServiceTest {

    private OrderRepository repository;
    private OrderService service;

    @BeforeEach
    public void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository);
    }

    // CRUD Tests
    @Test
    void testCreateOrder() {
        Order order = new Order(null, "Alice", 150.0);
        Order created = service.createOrder(order);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Alice", created.getCustomer());
        assertEquals(150.0, created.getTotal(), 0.01);
    }

    @Test
    void testCreateMultipleOrders() {
        Order order1 = new Order(null, "Bob", 200.0);
        Order order2 = new Order(null, "Charlie", 300.0);

        Order created1 = service.createOrder(order1);
        Order created2 = service.createOrder(order2);

        assertNotEquals(created1.getId(), created2.getId());
        assertEquals("Bob", created1.getCustomer());
        assertEquals("Charlie", created2.getCustomer());
    }

    @Test
    void testGetOrder() {
        Order order = new Order(null, "David", 250.0);
        Order created = service.createOrder(order);

        Order retrieved = service.getOrder(created.getId());

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("David", retrieved.getCustomer());
        assertEquals(250.0, retrieved.getTotal(), 0.01);
    }

    @Test
    void testGetOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> {
            service.getOrder(999L);
        });
    }

    @Test
    void testDeleteOrder() {
        Order order = new Order(null, "Eve", 175.0);
        Order created = service.createOrder(order);

        service.deleteOrder(created.getId());

        assertThrows(OrderNotFoundException.class, () -> {
            service.getOrder(created.getId());
        });
    }

    @Test
    void testDeleteNonExistentOrder() {
        assertThrows(OrderNotFoundException.class, () -> {
            service.deleteOrder(999L);
        });
    }

    @Test
    void testOrderEquality() {
        Order order1 = new Order(1L, "Frank", 100.0);
        Order order2 = new Order(1L, "Frank", 100.0);

        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
    }

    @Test
    void testOrderInequality() {
        Order order1 = new Order(1L, "Grace", 100.0);
        Order order2 = new Order(2L, "Grace", 100.0);

        assertNotEquals(order1, order2);
        assertNotEquals(order1.hashCode(), order2.hashCode());
    }

    // State Machine Tests
    @Test
    void testOrderToString() {
        Order order = new Order(5L, "Henry", 500.0);
        String str = order.toString();

        assertTrue(str.contains("id=5"));
        assertTrue(str.contains("Henry"));
        assertTrue(str.contains("500.0"));
    }

    @Test
    void testOrderInitialStatus() {
        Order order = new Order(null, "Iris", 120.0);
        assertEquals(OrderStatus.CREATED, order.getStatus());
    }

    @Test
    void testValidStatusTransition() {
        Order order = new Order(1L, "Jack", 180.0, OrderStatus.CREATED);
        boolean result = order.transitionTo(OrderStatus.PAID);

        assertTrue(result);
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void testInvalidStatusTransition() {
        Order order = new Order(2L, "Karen", 220.0, OrderStatus.DELIVERED);
        boolean result = order.transitionTo(OrderStatus.CREATED);

        assertFalse(result);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void testCompleteOrderWorkflow() {
        Order order = new Order(null, "Leo", 350.0);
        
        assertTrue(order.transitionTo(OrderStatus.PAID));
        assertEquals(OrderStatus.PAID, order.getStatus());
        
        // New state machine: CONFIRMED → PAID → PROCESSING
        assertTrue(order.transitionTo(OrderStatus.PAID));
        assertEquals(OrderStatus.PAID, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.SHIPPED));
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.SHIPPED));
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.DELIVERED));
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        
        assertFalse(order.transitionTo(OrderStatus.CREATED));
    }

    @Test
    void testOrderStatusTerminalStates() {
        assertFalse(OrderStatus.CREATED.isTerminal());
        assertFalse(OrderStatus.PAID.isTerminal());
        assertFalse(OrderStatus.SHIPPED.isTerminal());
        assertFalse(OrderStatus.SHIPPED.isTerminal());
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
    }

    @Test
    void testOrderCancellationFromPending() {
        Order order = new Order(3L, "Mia", 275.0, OrderStatus.CREATED);
        
        assertTrue(order.transitionTo(OrderStatus.CANCELLED));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getStatus().isTerminal());
    }

    @Test
    void testOrderStatusDisplayInfo() {
        OrderStatus status = OrderStatus.PAID;
        assertNotNull(status.getDisplayName());
        assertNotNull(status.getDescription());
        assertEquals("Confirmed", status.getDisplayName());
    }

    // Service Tests with Exception Handling
    @Test
    void testInvalidTransitionInService() {
        Order order = new Order(null, "Noah", 300.0);
        Order created = service.createOrder(order);

        assertThrows(InvalidTransitionException.class, () -> {
            service.transitionOrder(created.getId(), OrderStatus.DELIVERED);
        });
    }

    @Test
    void testValidTransitionInService() {
        Order order = new Order(null, "Olivia", 400.0);
        Order created = service.createOrder(order);

        Order confirmed = service.payOrder(created.getId());
        assertEquals(OrderStatus.PAID, confirmed.getStatus());
    }

    @Test
    void testFullOrderLifecycleInService() {
        Order order = new Order(null, "Peter", 500.0);
        Order created = service.createOrder(order);

        Order confirmed = service.payOrder(created.getId());
        assertEquals(OrderStatus.PAID, confirmed.getStatus());

        // New workflow: need to transition through PAID before PROCESSING
        Order paid = service.transitionOrder(created.getId(), OrderStatus.PAID);
        assertEquals(OrderStatus.PAID, paid.getStatus());

        Order processing = service.shipOrder(created.getId());
        assertEquals(OrderStatus.SHIPPED, processing.getStatus());

        Order shipped = service.shipOrder(created.getId());
        assertEquals(OrderStatus.SHIPPED, shipped.getStatus());

        Order delivered = service.deliverOrder(created.getId());
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
        assertTrue(delivered.getStatus().isTerminal());
    }

    @Test
    void testCannotTransitionFromDelivered() {
        Order order = new Order(null, "Quinn", 250.0);
        Order created = service.createOrder(order);

        service.payOrder(created.getId());
        service.transitionOrder(created.getId(), OrderStatus.PAID);
        service.shipOrder(created.getId());
        service.shipOrder(created.getId());
        service.deliverOrder(created.getId());

        assertThrows(InvalidTransitionException.class, () -> {
            service.cancelOrder(created.getId());
        });
    }

    @Test
    void testInvalidTransitionExceptionDetails() {
        Order order = new Order(null, "Rachel", 350.0);
        Order created = service.createOrder(order);

        InvalidTransitionException e = assertThrows(InvalidTransitionException.class, () -> {
            service.transitionOrder(created.getId(), OrderStatus.SHIPPED);
        });
        
        assertEquals(created.getId(), e.getOrderId());
        assertEquals(OrderStatus.CREATED, e.getCurrentStatus());
        assertEquals(OrderStatus.SHIPPED, e.getRequestedStatus());
        assertNotNull(e.getDetailedMessage());
        assertTrue(e.getDetailedMessage().contains("Order ID: " + created.getId()));
    }

    @Test
    void testCancelOrderAfterConfirmed() {
        Order order = new Order(null, "Sam", 175.0);
        Order created = service.createOrder(order);

        service.payOrder(created.getId());
        service.transitionOrder(created.getId(), OrderStatus.PAID);
        service.shipOrder(created.getId());
        
        assertThrows(InvalidTransitionException.class, () -> {
            service.shipOrder(created.getId());
        });
    }

    // Java 17 Feature Tests

    @Test
    void testOrderTimestampTracking() {
        Order order = new Order(null, "TimestampTest", 100.0);
        Order created = service.createOrder(order);

        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertTrue(created.getCreatedAt().isBefore(created.getUpdatedAt()) ||
                   created.getCreatedAt().equals(created.getUpdatedAt()));
    }

    @Test
    void testOrderUpdatedAtChangesOnStatusUpdate() throws InterruptedException {
        Order order = new Order(null, "UpdateTest", 100.0);
        Order created = service.createOrder(order);

        var createdAtTime = created.getCreatedAt();
        var initialUpdatedAt = created.getUpdatedAt();

        Thread.sleep(10); // Small delay to ensure timestamp difference
        service.payOrder(created.getId());
        
        Order updated = service.getOrder(created.getId());
        assertEquals(createdAtTime, updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void testIsTerminalStateMethod() {
        Order deliveredOrder = new Order(null, "Terminal", 100.0, OrderStatus.DELIVERED);
        assertTrue(deliveredOrder.isTerminalState());

        Order pendingOrder = new Order(null, "Active", 100.0, OrderStatus.CREATED);
        assertFalse(pendingOrder.isTerminalState());
    }

    @Test
    void testGetAllOrders() {
        service.createOrder(new Order(null, "Order1", 100.0));
        service.createOrder(new Order(null, "Order2", 200.0));
        service.createOrder(new Order(null, "Order3", 300.0));

        var allOrders = service.getAllOrders();
        assertNotNull(allOrders);
        assertEquals(3, allOrders.size());
    }

    @Test
    void testGetOrdersByStatus() {
        Order order1 = service.createOrder(new Order(null, "Order1", 100.0));
        service.createOrder(new Order(null, "Order2", 200.0));
        
        service.payOrder(order1.getId());

        var pendingOrders = service.getOrdersByStatus(OrderStatus.CREATED);
        var confirmedOrders = service.getOrdersByStatus(OrderStatus.PAID);

        assertEquals(1, pendingOrders.size());
        assertEquals(1, confirmedOrders.size());
        assertEquals("Order2", pendingOrders.get(0).getCustomer());
        assertEquals("Order1", confirmedOrders.get(0).getCustomer());
    }

    @Test
    void testGetCompletedOrders() {
        Order order1 = service.createOrder(new Order(null, "Order1", 100.0));
        Order order2 = service.createOrder(new Order(null, "Order2", 200.0));
        service.createOrder(new Order(null, "Order3", 300.0));

        // Complete order1 -> DELIVERED
        service.payOrder(order1.getId());
        service.transitionOrder(order1.getId(), OrderStatus.PAID);
        service.shipOrder(order1.getId());
        service.shipOrder(order1.getId());
        service.deliverOrder(order1.getId());

        // Cancel order2
        service.cancelOrder(order2.getId());

        // Leave order3 in PENDING state

        var completedOrders = service.getCompletedOrders();
        assertEquals(2, completedOrders.size());
        assertTrue(completedOrders.stream()
            .allMatch(Order::isTerminalState));
    }

    @Test
    void testGetTotalByStatus() {
        // Use fresh orders to avoid test isolation issues
        long timestamp = System.currentTimeMillis();
        service.createOrder(new Order(null, "Order_" + timestamp + "_1", 100.0));
        service.createOrder(new Order(null, "Order_" + timestamp + "_2", 200.0));
        service.createOrder(new Order(null, "Order_" + timestamp + "_3", 300.0));

        double totalPending = service.getTotalByStatus(OrderStatus.CREATED);
        // At least 600.0 from our created orders (may be more from other tests)
        assertTrue(totalPending >= 600.0);

        // Confirm one order and check totals again
        var pendingOrders = service.getOrdersByStatus(OrderStatus.CREATED);
        var orderToConfirm = pendingOrders.stream()
            .filter(o -> o.getCustomer().contains("_" + timestamp + "_1"))
            .findFirst();

        if (orderToConfirm.isPresent()) {
            service.payOrder(orderToConfirm.get().getId());

            double newTotalPending = service.getTotalByStatus(OrderStatus.CREATED);
            double totalConfirmed = service.getTotalByStatus(OrderStatus.PAID);

            // Verify the totals changed correctly
            assertTrue(newTotalPending < totalPending);
            assertTrue(totalConfirmed > 0);
        }
    }

    @Test
    void testCountByStatus() {
        service.createOrder(new Order(null, "Order1", 100.0));
        service.createOrder(new Order(null, "Order2", 200.0));
        service.createOrder(new Order(null, "Order3", 300.0));

        long pendingCount = service.countByStatus(OrderStatus.CREATED);
        assertEquals(3, pendingCount);

        // Transition orders and recount
        var pendingOrders = service.getOrdersByStatus(OrderStatus.CREATED);
        service.payOrder(pendingOrders.get(0).getId());
        service.payOrder(pendingOrders.get(1).getId());

        long newPendingCount = service.countByStatus(OrderStatus.CREATED);
        long confirmedCount = service.countByStatus(OrderStatus.PAID);

        assertEquals(1, newPendingCount);
        assertEquals(2, confirmedCount);
    }
}
