package com.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

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

    @Before
    public void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository);
    }

    @Test
    public void testCreateOrder() {
        Order order = new Order(null, "Alice", 150.0);
        Order created = service.createOrder(order);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Alice", created.getCustomer());
        assertEquals(150.0, created.getTotal(), 0.01);
    }

    @Test
    public void testCreateMultipleOrders() {
        Order order1 = new Order(null, "Bob", 200.0);
        Order order2 = new Order(null, "Charlie", 300.0);

        Order created1 = service.createOrder(order1);
        Order created2 = service.createOrder(order2);

        assertNotEquals(created1.getId(), created2.getId());
        assertEquals("Bob", created1.getCustomer());
        assertEquals("Charlie", created2.getCustomer());
    }

    @Test
    public void testGetOrder() {
        Order order = new Order(null, "David", 250.0);
        Order created = service.createOrder(order);

        Order retrieved = service.getOrder(created.getId());

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("David", retrieved.getCustomer());
        assertEquals(250.0, retrieved.getTotal(), 0.01);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testGetOrderNotFound() {
        service.getOrder(999L);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testDeleteOrder() {
        Order order = new Order(null, "Eve", 175.0);
        Order created = service.createOrder(order);

        service.deleteOrder(created.getId());

        service.getOrder(created.getId());
    }

    @Test(expected = OrderNotFoundException.class)
    public void testDeleteNonExistentOrder() {
        service.deleteOrder(999L);
    }

    @Test
    public void testOrderEquality() {
        Order order1 = new Order(1L, "Frank", 100.0);
        Order order2 = new Order(1L, "Frank", 100.0);

        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
    }

    @Test
    public void testOrderInequality() {
        Order order1 = new Order(1L, "Grace", 100.0);
        Order order2 = new Order(2L, "Grace", 100.0);

        assertNotEquals(order1, order2);
        assertNotEquals(order1.hashCode(), order2.hashCode());
    }

    @Test
    public void testOrderToString() {
        Order order = new Order(5L, "Henry", 500.0);
        String str = order.toString();

        assertTrue(str.contains("id=5"));
        assertTrue(str.contains("Henry"));
        assertTrue(str.contains("500.0"));
    }

    @Test
    public void testOrderInitialStatus() {
        Order order = new Order(null, "Iris", 120.0);
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    public void testValidStatusTransition() {
        Order order = new Order(1L, "Jack", 180.0, OrderStatus.PENDING);
        boolean result = order.transitionTo(OrderStatus.CONFIRMED);

        assertTrue(result);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    public void testInvalidStatusTransition() {
        Order order = new Order(2L, "Karen", 220.0, OrderStatus.DELIVERED);
        boolean result = order.transitionTo(OrderStatus.PENDING);

        assertFalse(result);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    public void testCompleteOrderWorkflow() {
        Order order = new Order(null, "Leo", 350.0);
        
        // PENDING -> CONFIRMED
        assertTrue(order.transitionTo(OrderStatus.CONFIRMED));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        
        // CONFIRMED -> PROCESSING
        assertTrue(order.transitionTo(OrderStatus.PROCESSING));
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        
        // PROCESSING -> SHIPPED
        assertTrue(order.transitionTo(OrderStatus.SHIPPED));
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        
        // SHIPPED -> DELIVERED (terminal)
        assertTrue(order.transitionTo(OrderStatus.DELIVERED));
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        
        // DELIVERED is terminal - no further transitions
        assertFalse(order.transitionTo(OrderStatus.PENDING));
    }

    @Test
    public void testOrderStatusTerminalStates() {
        assertFalse(OrderStatus.PENDING.isTerminal());
        assertFalse(OrderStatus.CONFIRMED.isTerminal());
        assertFalse(OrderStatus.PROCESSING.isTerminal());
        assertFalse(OrderStatus.SHIPPED.isTerminal());
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.FAILED.isTerminal());
    }

    @Test
    public void testOrderCancellationFromPending() {
        Order order = new Order(3L, "Mia", 275.0, OrderStatus.PENDING);
        
        assertTrue(order.transitionTo(OrderStatus.CANCELLED));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getStatus().isTerminal());
    }

    @Test
    public void testOrderStatusDisplayInfo() {
        OrderStatus status = OrderStatus.CONFIRMED;
        assertNotNull(status.getDisplayName());
        assertNotNull(status.getDescription());
        assertEquals("Confirmed", status.getDisplayName());
    }

    @Test(expected = InvalidTransitionException.class)
    public void testInvalidTransitionInService() {
        Order order = new Order(null, "Noah", 300.0);
        Order created = service.createOrder(order);

        // Try to transition from PENDING directly to DELIVERED (invalid)
        service.transitionOrder(created.getId(), OrderStatus.DELIVERED);
    }

    @Test
    public void testValidTransitionInService() {
        Order order = new Order(null, "Olivia", 400.0);
        Order created = service.createOrder(order);

        Order confirmed = service.confirmOrder(created.getId());
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    public void testFullOrderLifecycleInService() {
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

    @Test(expected = InvalidTransitionException.class)
    public void testCannotTransitionFromDelivered() {
        Order order = new Order(null, "Quinn", 250.0);
        Order created = service.createOrder(order);

        service.confirmOrder(created.getId());
        service.processOrder(created.getId());
        service.shipOrder(created.getId());
        service.deliverOrder(created.getId());

        // Try to transition from terminal state
        service.cancelOrder(created.getId());
    }

    @Test
    public void testInvalidTransitionExceptionDetails() {
        Order order = new Order(null, "Rachel", 350.0);
        Order created = service.createOrder(order);

        try {
            service.transitionOrder(created.getId(), OrderStatus.SHIPPED);
            assertTrue("Expected InvalidTransitionException", false);
        } catch (InvalidTransitionException e) {
            assertEquals(created.getId(), e.getOrderId());
            assertEquals(OrderStatus.PENDING, e.getCurrentStatus());
            assertEquals(OrderStatus.SHIPPED, e.getRequestedStatus());
            assertNotNull(e.getDetailedMessage());
            assertTrue(e.getDetailedMessage().contains("Order ID: " + created.getId()));
        }
    }

    @Test(expected = InvalidTransitionException.class)
    public void testCancelOrderAfterConfirmed() {
        Order order = new Order(null, "Sam", 175.0);
        Order created = service.createOrder(order);

        service.confirmOrder(created.getId());
        // Try to cancel from PROCESSING (not allowed from CONFIRMED when in PROCESSING)
        service.processOrder(created.getId());
        service.processOrder(created.getId()); // Double process should fail
    }
}
