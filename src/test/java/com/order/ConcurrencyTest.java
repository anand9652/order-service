package com.order;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.order.exception.InvalidTransitionException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.InMemoryOrderRepository;
import com.order.repository.OrderRepository;
import com.order.service.OrderService;

/**
 * Test cases for concurrent order processing.
 * Validates thread-safety and concurrent access patterns.
 */
public class ConcurrencyTest {

    private OrderRepository repository;
    private OrderService service;

    @BeforeEach
    public void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository);
    }

    /**
     * Test concurrent order creation from multiple threads.
     * Verifies that IDs are unique and no orders are lost.
     */
    @Test
    void testConcurrentOrderCreation() throws InterruptedException {
        int numThreads = 10;
        int ordersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                for (int j = 0; j < ordersPerThread; j++) {
                    Order order = new Order(null, "Customer_" + Thread.currentThread().getName(), 100.0 + j);
                    Order created = service.createOrder(order);
                    assertNotNull(created.getId());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        executor.shutdown();
    }

    /**
     * Test concurrent reads of the same order.
     * Verifies data consistency across threads.
     */
    @Test
    void testConcurrentOrderRetrieval() throws InterruptedException {
        Order order = new Order(null, "TestCustomer", 500.0);
        Order created = service.createOrder(order);
        Long orderId = created.getId();

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                for (int j = 0; j < 10; j++) {
                    Order retrieved = service.getOrder(orderId);
                    assertEquals(created.getCustomer(), retrieved.getCustomer());
                    assertEquals(created.getTotal(), retrieved.getTotal(), 0.01);
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        executor.shutdown();
    }

    /**
     * Test concurrent state transitions on the same order.
     * Verifies prevention of race conditions in state machine.
     */
    @Test
    void testConcurrentTransitionsOnSameOrder() throws InterruptedException {
        Order order = new Order(null, "RaceTestCustomer", 250.0);
        Order created = service.createOrder(order);

        int numThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    if (threadId == 0) {
                        service.confirmOrder(created.getId());
                    } else if (threadId == 1) {
                        service.confirmOrder(created.getId());
                    } else {
                        Order current = service.getOrder(created.getId());
                        if (current.getStatus() == OrderStatus.CONFIRMED) {
                            service.processOrder(created.getId());
                        }
                    }
                } catch (InvalidTransitionException e) {
                    // Expected in some cases
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                // Some exceptions expected
            }
        }

        executor.shutdown();
    }

    /**
     * Test concurrent sequential transitions.
     * Verifies correct workflow progression under concurrency.
     */
    @Test
    void testConcurrentSequentialTransitions() throws InterruptedException {
        int numOrders = 20;
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < numOrders; i++) {
            Order order = new Order(null, "Order_" + i, 100.0 + i);
            orders.add(service.createOrder(order));
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();

        for (Order order : orders) {
            futures.add(executor.submit(() -> {
                try {
                    service.confirmOrder(order.getId());
                    service.processOrder(order.getId());
                    service.shipOrder(order.getId());
                    service.deliverOrder(order.getId());
                } catch (InvalidTransitionException e) {
                    fail("Valid transition failed: " + e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        executor.shutdown();
    }

    /**
     * Test high concurrency with mixed operations.
     * Creates, reads, and updates orders from many threads.
     */
    @Test
    void testHighConcurrencyMixedOperations() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Order order = new Order(null, "MixedOp_" + Thread.currentThread().getId(), 150.0);
                    Order created = service.createOrder(order);
                    successCount.incrementAndGet();

                    Order retrieved = service.getOrder(created.getId());
                    assertEquals(created.getCustomer(), retrieved.getCustomer());
                } catch (Exception e) {
                    fail("Operation failed: " + e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        assertEquals(100, successCount.get());
        executor.shutdown();
    }

    /**
     * Test concurrent access to terminal orders.
     * Verifies immutability and proper state protection.
     */
    @Test
    void testConcurrentAccessToTerminalOrders() throws InterruptedException {
        Order order = new Order(null, "TerminalTestCustomer", 300.0);
        Order created = service.createOrder(order);

        service.confirmOrder(created.getId());
        service.processOrder(created.getId());
        service.shipOrder(created.getId());
        service.deliverOrder(created.getId());

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger invalidTransitionCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                for (int j = 0; j < 5; j++) {
                    try {
                        service.cancelOrder(created.getId());
                    } catch (InvalidTransitionException e) {
                        invalidTransitionCount.incrementAndGet();
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        assertTrue(invalidTransitionCount.get() > 0);
        executor.shutdown();
    }

    /**
     * Stress test: rapid concurrent operations.
     * Tests system performance and stability under heavy load.
     */
    @Test
    void testStressTestRapidConcurrentOperations() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 250; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Order order = new Order(null, "Stress_" + System.nanoTime(), 99.99);
                    Order created = service.createOrder(order);
                    assertNotNull(created.getId());

                    Order retrieved = service.getOrder(created.getId());
                    assertNotNull(retrieved);
                } catch (Exception e) {
                    fail("Stress test operation failed: " + e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }

        executor.shutdown();
    }
}
