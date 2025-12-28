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
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void testValidStatusTransition() {
        Order order = new Order(1L, "Jack", 180.0, OrderStatus.PENDING);
        boolean result = order.transitionTo(OrderStatus.CONFIRMED);

        assertTrue(result);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void testInvalidStatusTransition() {
        Order order = new Order(2L, "Karen", 220.0, OrderStatus.DELIVERED);
        boolean result = order.transitionTo(OrderStatus.PENDING);

        assertFalse(result);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void testCompleteOrderWorkflow() {
        Order order = new Order(null, "Leo", 350.0);
        
        assertTrue(order.transitionTo(OrderStatus.CONFIRMED));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.PROCESSING));
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.SHIPPED));
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        
        assertTrue(order.transitionTo(OrderStatus.DELIVERED));
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        
        assertFalse(order.transitionTo(OrderStatus.PENDING));
    }

    @Test
    void testOrderStatusTerminalStates() {
        assertFalse(OrderStatus.PENDING.isTerminal());
        assertFalse(OrderStatus.CONFIRMED.isTerminal());
        assertFalse(OrderStatus.PROCESSING.isTerminal());
        assertFalse(OrderStatus.SHIPPED.isTerminal());
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.FAILED.isTerminal());
    }

    @Test
    void testOrderCancellationFromPending() {
        Order order = new Order(3L, "Mia", 275.0, OrderStatus.PENDING);
        
        assertTrue(order.transitionTo(OrderStatus.CANCELLED));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getStatus().isTerminal());
    }

    @Test
    void testOrderStatusDisplayInfo() {
        OrderStatus status = OrderStatus.CONFIRMED;
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

        Order confirmed = service.confirmOrder(created.getId());
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void testFullOrderLifecycleInService() {
        Order order = new Order(null, "Peter", 500.0);
        Order created = service.createOrder(order);

        Order confirmed = service.confirmOrder(created.getId());
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());

        Order processing = service.processOrder(created.getId());
        assertEquals(OrderStatus.PROCESSING, processing.getStatus());

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

        service.confirmOrder(created.getId());
        service.processOrder(created.getId());
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
        assertEquals(OrderStatus.PENDING, e.getCurrentStatus());
        assertEquals(OrderStatus.SHIPPED, e.getRequestedStatus());
        assertNotNull(e.getDetailedMessage());
        assertTrue(e.getDetailedMessage().contains("Order ID: " + created.getId()));
    }

    @Test
    void testCancelOrderAfterConfirmed() {
        Order order = new Order(null, "Sam", 175.0);
        Order created = service.createOrder(order);

        service.confirmOrder(created.getId());
        service.processOrder(created.getId());
        
        assertThrows(InvalidTransitionException.class, () -> {
            service.processOrder(created.getId());
        });
    }
}
