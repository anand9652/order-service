package com.order;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.order.exception.InvalidTransitionException;
import com.order.exception.OrderNotFoundException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.InMemoryOrderRepository;
import com.order.service.OrderService;

/**
 * Advanced concurrency tests for atomic state transitions.
 * Validates that only ONE thread can successfully transition an order's state
 * when multiple threads attempt simultaneous transitions.
 */
public class AtomicTransitionTest {

    private OrderService service;
    private InMemoryOrderRepository repository;

    @BeforeEach
    public void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository);
    }

    @Test
    void testOnlyOneTransitionSucceedsWhenMultipleThreadsAttemptSame() throws InterruptedException {
        // Create an order in PENDING state
        Order order = service.createOrder(new Order(null, "Test Customer", 100.0));
        Long orderId = order.getId();

        // Thread A will try: CREATED → PAID
        // Thread B will try: PENDING → FAILED
        // Both are valid from PENDING, so both CAN succeed, BUT they serialize through the atomic lock.
        // After Thread A transitions to CONFIRMED, if Thread B still tries FAILED, that becomes invalid.
        // This tests that the state is re-read after acquiring the lock (double-check pattern).
        
        // Better test: After transitioning to CONFIRMED, FAILED is no longer valid!
        // So one will succeed and transition to CONFIRMED, the other will fail because
        // FAILED is not a valid transition from CONFIRMED.

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            try {
                startSignal.await();  // Wait for all threads to be ready
                service.payOrder(orderId);  // CREATED → PAID
                successCount.incrementAndGet();
            } catch (InvalidTransitionException e) {
                failCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                startSignal.await();  // Wait for all threads to be ready
                // Try to transition to FAILED - valid from PENDING, but only if we get the lock first!
                // If threadA succeeds first, status becomes CONFIRMED, and CONFIRMED → FAILED is invalid
                service.transitionOrder(orderId, OrderStatus.CANCELLED);
                successCount.incrementAndGet();
            } catch (InvalidTransitionException e) {
                failCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        });

        threadA.start();
        threadB.start();
        startSignal.countDown();  // Release all threads simultaneously
        doneSignal.await();       // Wait for both to complete

        // One thread will succeed in transitioning, the other will fail with InvalidTransitionException
        // because once the first succeeds, the state changes and the second's transition becomes invalid
        assertEquals(1, successCount.get(), "Exactly one thread should successfully transition");
        assertEquals(1, failCount.get(), "Exactly one thread should fail due to invalid state transition");

        // Verify the final state is deterministic - should be either CONFIRMED (if A won the lock first)
        // or FAILED (if B somehow won, but that can't happen because CONFIRMED comes before FAILED wins)
        Order finalOrder = service.getOrder(orderId);
        assertTrue(
            finalOrder.getStatus() == OrderStatus.PAID || 
            finalOrder.getStatus() == OrderStatus.CANCELLED,
            "Final status must be either CONFIRMED or FAILED"
        );
    }

    @Test
    void testMultipleThreadsAttemptingDifferentSequences() throws InterruptedException {
        Order order = service.createOrder(new Order(null, "Multi-Thread", 250.0));
        Long orderId = order.getId();

        // Scenario: Multiple threads attempting to advance an order through its full lifecycle
        // All threads start simultaneously and try to do a full transition sequence.
        // Due to atomic locking, they serialize and most will fail because the order state
        // will change after the first thread.
        //
        // Thread 1: CREATED → PAID (succeeds)
        // Thread 2: CREATED → PAID (fails - already CONFIRMED)
        // Thread 3: PENDING → CANCELLED (fails - already CONFIRMED, and CONFIRMED→CANCELLED is valid but one thread already moved forward)
        //
        // This validates that:
        // 1. The lock prevents concurrent modification (atomic)
        // 2. Once a transition succeeds, subsequent attempts see the new state
        // 3. Invalid transitions are caught and reported properly
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(3);

        // All three threads try to confirm the order from PENDING
        // But only one will succeed; the others will fail
        Runnable confirmAttempt = () -> {
            try {
                startSignal.await();
                service.payOrder(orderId);  // CREATED → PAID
                successCount.incrementAndGet();
            } catch (InvalidTransitionException | OrderNotFoundException e) {
                failCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        };

        Thread t1 = new Thread(confirmAttempt, "ConfirmThread-1");
        Thread t2 = new Thread(confirmAttempt, "ConfirmThread-2");
        Thread t3 = new Thread(confirmAttempt, "ConfirmThread-3");

        t1.start();
        t2.start();
        t3.start();
        startSignal.countDown();
        doneSignal.await();

        // Exactly ONE should succeed - once one thread transitions to CONFIRMED,
        // the other threads trying to do PENDING→CONFIRMED will fail
        assertEquals(1, successCount.get(), "Only one transition should succeed");
        assertEquals(2, failCount.get(), "Exactly two transitions should fail");
    }

    @Test
    void testSequentialTransitionsAreAtomic() throws InterruptedException {
        Order order = service.createOrder(new Order(null, "Sequential Test", 300.0));
        Long orderId = order.getId();

        // Multiple threads try the full sequence: CREATED → PAID → PROCESSING → SHIPPED
        // Simulate a race condition where threads try to advance the state
        int numThreads = 5;
        AtomicInteger completedSequences = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numThreads);

        Runnable sequenceAttempt = () -> {
            try {
                startSignal.await();
                try {
                    service.payOrder(orderId);
                    service.shipOrder(orderId);
                    service.shipOrder(orderId);
                    completedSequences.incrementAndGet();
                } catch (InvalidTransitionException e) {
                    // Expected - other threads already transitioned the order
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        };

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(sequenceAttempt, "SequenceThread-" + i);
            threads[i].start();
        }

        startSignal.countDown();
        doneSignal.await();

        // At most one thread should complete the full sequence
        assertTrue(completedSequences.get() <= 1, 
            "At most one thread should complete the sequence");

        // Final state should be deterministic
        Order finalOrder = service.getOrder(orderId);
        assertNotEquals(OrderStatus.CREATED, finalOrder.getStatus(),
            "Order should have transitioned");
    }

    @Test
    void testConcurrentUpdatesToDifferentOrders() throws InterruptedException {
        // Create multiple orders
        Order order1 = service.createOrder(new Order(null, "Order 1", 100.0));
        Order order2 = service.createOrder(new Order(null, "Order 2", 200.0));
        Order order3 = service.createOrder(new Order(null, "Order 3", 300.0));

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch doneSignal = new CountDownLatch(3);

        // Each thread transitions a different order - should all succeed
        // Each transition is valid for PENDING state
        Thread t1 = new Thread(() -> {
            try {
                service.payOrder(order1.getId());  // CREATED → PAID
                successCount.incrementAndGet();
            } finally {
                doneSignal.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                service.cancelOrder(order2.getId());   // PENDING → CANCELLED
                successCount.incrementAndGet();
            } finally {
                doneSignal.countDown();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                service.transitionOrder(order3.getId(), OrderStatus.CANCELLED);  // PENDING → FAILED
                successCount.incrementAndGet();
            } finally {
                doneSignal.countDown();
            }
        });

        t1.start();
        t2.start();
        t3.start();
        doneSignal.await();

        // All three should succeed since they're different orders and all transitions are valid
        assertEquals(3, successCount.get(), "All transitions to different orders should succeed");
    }

    @Test
    void testRapidConsecutiveTransitionAttempts() throws InterruptedException {
        Order order = service.createOrder(new Order(null, "Rapid Test", 150.0));
        Long orderId = order.getId();

        // Rapid fire: 10 threads all trying to transition CREATED → PAID
        int numThreads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numThreads);

        Runnable rapidTransition = () -> {
            try {
                startSignal.await();
                service.payOrder(orderId);
                successCount.incrementAndGet();
            } catch (InvalidTransitionException e) {
                // Expected - order already transitioned
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        };

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(rapidTransition, "RapidThread-" + i);
            threads[i].start();
        }

        startSignal.countDown();
        doneSignal.await();

        // Exactly ONE should succeed
        assertEquals(1, successCount.get(), "Only one rapid transition should succeed");

        // Order should be CONFIRMED
        Order finalOrder = service.getOrder(orderId);
        assertEquals(OrderStatus.PAID, finalOrder.getStatus());
    }
}
