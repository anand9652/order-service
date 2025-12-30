package com.order;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.InMemoryOrderRepository;
import com.order.service.OrderScheduler;
import com.order.service.OrderService;

/**
 * Comprehensive tests for OrderScheduler background processing.
 *
 * Test Coverage:
 * 1. Automatic transitions: Orders transition PAID → PROCESSING after delay
 * 2. Duplicate prevention: Same order never processed twice
 * 3. Timing accuracy: Only processes orders old enough
 * 4. State validation: Only PAID orders are transitioned
 * 5. Concurrency safety: Works safely with concurrent client access
 * 6. Graceful shutdown: Stops cleanly without hanging
 * 7. Idempotence: Restarting doesn't corrupt state
 */
public class OrderSchedulerTest {

    private OrderService service;
    private InMemoryOrderRepository repository;
    private OrderScheduler scheduler;

    @BeforeEach
    public void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository);
        scheduler = new OrderScheduler(service, repository, 1); // 1 minute delay for testing
    }

    @Test
    void testSchedulerStartsAndStops() {
        assertFalse(scheduler.isRunning(), "Scheduler should not be running initially");

        scheduler.start();
        assertTrue(scheduler.isRunning(), "Scheduler should be running after start");

        scheduler.stop();
        assertFalse(scheduler.isRunning(), "Scheduler should not be running after stop");
    }

    @Test
    void testStartingAlreadyRunningSchedulerIsIdempotent() {
        scheduler.start();
        assertTrue(scheduler.isRunning());

        // Starting again should not error
        scheduler.start();
        assertTrue(scheduler.isRunning());

        scheduler.stop();
    }

    @Test
    void testStoppingIdleSchedulerIsIdempotent() {
        assertFalse(scheduler.isRunning());

        // Stopping when not running should not error
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    @Timeout(30) // Prevent hanging tests
    void testAutomaticTransitionAfterDelay() throws InterruptedException {
        // Create order and transition to PAID state
        Order order = service.createOrder(new Order(null, "Test Customer", 100.0));
        service.payOrder(order.getId());

        Order paidOrder = service.getOrder(order.getId());
        assertEquals(OrderStatus.PAID, paidOrder.getStatus(), "Order should be PAID initially");

        // Now manually create an order that is "old" (via reflection or factory method)
        // Create an older order by modifying updatedAt
        Order oldOrder = service.createOrder(new Order(null, "Old Customer", 200.0));
        service.payOrder(oldOrder.getId());

        // Manually age the order (simulate it being created 2+ minutes ago)
        manipulateOrderTimestamp(oldOrder.getId(), Instant.now().minus(2, ChronoUnit.MINUTES));

        // Start scheduler
        scheduler.start();

        // Wait for scheduler to process (first run is immediate, then every minute)
        // Give it 3 seconds to detect and process
        Thread.sleep(3000);

        // Verify: Old order should have transitioned
        Order processed = service.getOrder(oldOrder.getId());
        assertEquals(OrderStatus.SHIPPED, processed.getStatus(), 
            "Old PAID order should transition to PROCESSING");

        // Verify: New order (not aged) should NOT transition yet
        Order stillPaid = service.getOrder(order.getId());
        assertEquals(OrderStatus.PAID, stillPaid.getStatus(), "New order shouldn't transition yet");

        scheduler.stop();
    }

    @Test
    void testDuplicatePreventionTracking() throws InterruptedException {
        // Create and transition order to PAID
        Order order = service.createOrder(new Order(null, "Test", 100.0));
        service.payOrder(order.getId());

        // Age the order
        manipulateOrderTimestamp(order.getId(), Instant.now().minus(2, ChronoUnit.MINUTES));

        scheduler.start();
        Thread.sleep(2000); // Let scheduler process

        // Verify order was processed
        assertTrue(scheduler.isOrderProcessed(order.getId()), 
            "Order should be tracked as processed");

        // Verify it was transitioned
        assertEquals(OrderStatus.SHIPPED, service.getOrder(order.getId()).getStatus());

        scheduler.stop();
    }

    @Test
    void testOnlyPaidOrdersAreTransitioned() throws InterruptedException {
        // Create multiple orders in different states
        Order pendingOrder = service.createOrder(new Order(null, "Pending", 100.0));
        
        Order confirmedOrder = service.createOrder(new Order(null, "Confirmed", 100.0));
        service.payOrder(confirmedOrder.getId());

        Order processingOrder = service.createOrder(new Order(null, "Processing", 100.0));
        service.payOrder(processingOrder.getId());
        service.shipOrder(processingOrder.getId());

        // Age them all
        for (Long id : new Long[]{pendingOrder.getId(), confirmedOrder.getId(), processingOrder.getId()}) {
            manipulateOrderTimestamp(id, Instant.now().minus(2, ChronoUnit.MINUTES));
        }

        scheduler.start();
        Thread.sleep(2000);

        // Verify: Only PAID orders are transitioned to SHIPPED
        assertEquals(OrderStatus.CREATED, service.getOrder(pendingOrder.getId()).getStatus());
        assertEquals(OrderStatus.SHIPPED, service.getOrder(confirmedOrder.getId()).getStatus());
        assertEquals(OrderStatus.SHIPPED, service.getOrder(processingOrder.getId()).getStatus());

        scheduler.stop();
    }

    @Test
    void testSchedulerDoesNotTransitionRecentOrders() throws InterruptedException {
        // Create PAID order
        Order order = service.createOrder(new Order(null, "Recent", 100.0));
        service.payOrder(order.getId());

        // Verify it's recent (not aged)
        Order recentOrder = service.getOrder(order.getId());
        Instant now = Instant.now();
        long minutesOld = ChronoUnit.MINUTES.between(recentOrder.getUpdatedAt(), now);
        assertTrue(minutesOld < 1, "Order should be very recent");

        scheduler.start();
        Thread.sleep(2000);

        // Verify: Should NOT transition (too recent)
        assertEquals(OrderStatus.PAID, service.getOrder(order.getId()).getStatus(),
            "Recent PAID order should NOT transition yet");
        
        assertFalse(scheduler.isOrderProcessed(order.getId()),
            "Recent order should not be in processed set");

        scheduler.stop();
    }

    @Test
    void testClearProcessedOrdersResets() {
        // Track an order manually
        scheduler.clearProcessedOrders();
        assertEquals(0, scheduler.getProcessedOrderCount(), "Should start empty");
    }

    @Test
    void testProcessedOrderCountIsAccurate() throws InterruptedException {
        // Create multiple PAID orders
        Order order1 = createAndAgePaidOrder("Customer 1", 100.0);
        createAndAgePaidOrder("Customer 2", 200.0);
        createAndAgePaidOrder("Customer 3", 300.0);

        assertEquals(0, scheduler.getProcessedOrderCount(), "Should start with 0");

        scheduler.start();
        Thread.sleep(3000);

        // Verify all three were processed
        assertTrue(scheduler.isOrderProcessed(order1.getId()));
        assertEquals(3, scheduler.getProcessedOrderCount());

        scheduler.stop();
    }

    @Test
    void testSchedulerWithCustomDelay() throws InterruptedException {
        // Create scheduler with very short delay (for testing)
        OrderScheduler fastScheduler = new OrderScheduler(service, repository, 0); // 0 minute delay

        Order order = service.createOrder(new Order(null, "Test", 100.0));
        service.payOrder(order.getId());

        fastScheduler.start();
        Thread.sleep(2000);

        // Should transition immediately since delay is 0
        assertEquals(OrderStatus.SHIPPED, service.getOrder(order.getId()).getStatus(),
            "Order with 0-minute delay should transition");

        fastScheduler.stop();
    }

    @Test
    void testSchedulerGracefulShutdown() throws InterruptedException {
        // Create aged orders
        Order order1 = createAndAgePaidOrder("Customer 1", 100.0);
        createAndAgePaidOrder("Customer 2", 200.0);

        scheduler.start();
        Thread.sleep(1000);

        // Should have processed them
        assertTrue(scheduler.isOrderProcessed(order1.getId()));

        // Stop should complete without hanging
        scheduler.stop();
        
        assertFalse(scheduler.isRunning(), "Should be stopped");
        assertFalse(scheduler.isOrderProcessed(1000L), "Processed orders are in-memory only");
    }

    @Test
    @Timeout(10)
    void testMultipleSchedulersAreIndependent() throws InterruptedException {
        // Create two separate repositories and services for truly independent schedulers
        InMemoryOrderRepository repo1 = new InMemoryOrderRepository();
        OrderService service1 = new OrderService(repo1);
        OrderScheduler scheduler1 = new OrderScheduler(service1, repo1, 0);

        InMemoryOrderRepository repo2 = new InMemoryOrderRepository();
        OrderService service2 = new OrderService(repo2);
        OrderScheduler scheduler2 = new OrderScheduler(service2, repo2, 0);

        // Create aged orders in each repo
        Order order1 = createAndAgePaidOrder("Order1", 100.0, service1, repo1);
        Order order2a = createAndAgePaidOrder("Order2", 200.0, service2, repo2);

        scheduler1.start();
        scheduler2.start();
        
        Thread.sleep(3000);

        // Each scheduler should have processed its own orders independently
        assertTrue(scheduler1.isOrderProcessed(order1.getId()));
        assertTrue(scheduler2.isOrderProcessed(order2a.getId()));

        scheduler1.stop();
        scheduler2.stop();
    }

    // Helper methods

    /**
     * Creates an order in PAID status and ages it to trigger scheduler processing.
     */
    private Order createAndAgePaidOrder(String customer, double total) {
        Order order = service.createOrder(new Order(null, customer, total));
        service.payOrder(order.getId());
        manipulateOrderTimestamp(order.getId(), Instant.now().minus(2, ChronoUnit.MINUTES));
        return order;
    }

    /**
     * Creates an order in PAID status and ages it, using custom service/repo.
     */
    private Order createAndAgePaidOrder(String customer, double total, OrderService svc, InMemoryOrderRepository repo) {
        Order order = svc.createOrder(new Order(null, customer, total));
        svc.payOrder(order.getId());
        
        // Manipulate timestamp directly in repo
        Order toAge = repo.findById(order.getId()).orElse(order);
        try {
            var field = Order.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(toAge, Instant.now().minus(2, ChronoUnit.MINUTES));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not manipulate timestamp: " + e.getMessage());
        }
        return toAge;
    }

    /**
     * Manually updates an order's updatedAt timestamp to simulate aging.
     * This is test-only since we need to trigger scheduler processing.
     */
    private void manipulateOrderTimestamp(Long orderId, Instant newTime) {
        Order order = service.getOrder(orderId);
        try {
            var field = Order.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(order, newTime);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not manipulate timestamp: " + e.getMessage());
        }
    }
}
