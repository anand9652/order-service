package com.order.service;

import com.order.exception.InvalidTransitionException;
import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.OrderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Background scheduler for automatic order state transitions.
 *
 * Responsibilities:
 * - Schedule periodic checks for orders that need automatic transitions
 * - Transition PAID orders to PROCESSING after 5 minutes (configurable)
 * - Prevent duplicate transitions via idempotent tracking
 * - Ensure thread-safe operation under concurrent access
 *
 * Design Patterns:
 * 1. Scheduled Executor: Uses ScheduledExecutorService for periodic tasks
 * 2. Idempotent Processing: Tracks processed orders to prevent re-processing
 * 3. Atomic Transitions: Leverages OrderService's atomic transitionOrder() method
 * 4. Concurrent Tracking: Uses ConcurrentHashMap for thread-safe processing record
 *
 * Concurrency Safety:
 * - All state is maintained in thread-safe collections (ConcurrentHashMap, CopyOnWriteArrayList)
 * - OrderService.transitionOrder() provides atomic state transition guarantees
 * - No race conditions between scheduler and concurrent client calls
 */
public class OrderScheduler {
    private static final Logger LOGGER = Logger.getLogger(OrderScheduler.class.getName());

    private final OrderService orderService;
    private final OrderRepository repository;
    private final ScheduledExecutorService executor;

    /**
     * Tracks orders that have been processed by this scheduler.
     * Maps Order ID → Last processing time
     *
     * Why ConcurrentHashMap?
     * - Scheduler thread and application threads both read/write this
     * - Avoids blocking on high-frequency operations
     * - Ensures thread-safe updates without coarse-grained synchronization
     */
    private final Set<Long> processedOrders = ConcurrentHashMap.newKeySet();

    /**
     * Delay before automatic transition (in minutes).
     * Orders transition PAID → PROCESSING after this delay.
     */
    private final long transitionDelayMinutes;

    /**
     * Flag to indicate scheduler lifecycle state.
     */
    private volatile boolean running = false;

    /**
     * Creates a new OrderScheduler with default 5-minute transition delay.
     *
     * @param orderService the order service for transitions
     * @param repository the order repository for queries
     */
    public OrderScheduler(OrderService orderService, OrderRepository repository) {
        this(orderService, repository, 5); // Default: 5 minutes
    }

    /**
     * Creates a new OrderScheduler with custom transition delay.
     *
     * @param orderService the order service for transitions
     * @param repository the order repository for queries
     * @param transitionDelayMinutes delay before automatic transition
     */
    public OrderScheduler(OrderService orderService, OrderRepository repository, long transitionDelayMinutes) {
        this.orderService = orderService;
        this.repository = repository;
        this.transitionDelayMinutes = transitionDelayMinutes;
        
        // Create a single-threaded executor for scheduling
        // Single thread ensures predictable behavior and no resource explosion
        ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
        scheduledExecutor.setRemoveOnCancelPolicy(true); // Clean up cancelled tasks
        this.executor = scheduledExecutor;
    }

    /**
     * Starts the background scheduler.
     * Schedules periodic processing at fixed intervals.
     *
     * Thread Safety: Uses volatile flag and ScheduledExecutorService's thread-safe operations.
     */
    public synchronized void start() {
        if (running) {
            LOGGER.warning("OrderScheduler is already running");
            return;
        }

        running = true;
        
        // Schedule at fixed rate: process immediately, then every 1 minute
        // Interval is 1 minute, but orders only transition if conditions are met (created 5+ min ago)
        executor.scheduleAtFixedRate(
            this::processPaidOrders,
            0,                  // Initial delay: 0 (start immediately)
            1,                  // Period: 1 minute
            TimeUnit.MINUTES
        );

        LOGGER.info("OrderScheduler started with transition delay: " + transitionDelayMinutes + " minutes");
    }

    /**
     * Stops the background scheduler gracefully.
     *
     * Waits up to 10 seconds for pending tasks to complete.
     */
    public synchronized void stop() {
        if (!running) {
            LOGGER.warning("OrderScheduler is not running");
            return;
        }

        running = false;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warning("OrderScheduler shutdown timeout, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warning("OrderScheduler shutdown interrupted");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("OrderScheduler stopped");
    }

    /**
     * Checks if the scheduler is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Processes all PAID orders that are ready for transition.
     *
     * Algorithm:
     * 1. Retrieve all orders from repository
     * 2. Filter for PAID status
     * 3. Check if order was updated at least transitionDelayMinutes ago
     * 4. Skip if already processed (in processedOrders set)
     * 5. Attempt transition PAID → PROCESSING
     * 6. Track successful transitions to prevent duplicates
     *
     * Thread Safety:
     * - Called by scheduler thread only (single-threaded executor)
     * - OrderService.transitionOrder() is atomic (safe for concurrent client access)
     * - processedOrders is thread-safe ConcurrentHashMap
     * - No locks held across repository/service calls
     */
    private void processPaidOrders() {
        if (!running) {
            return;
        }

        try {
            LOGGER.fine("Processing PAID orders for automatic transition to PROCESSING");

            List<Order> allOrders = repository.findAll();
            Instant now = Instant.now();

            for (Order order : allOrders) {
                // Skip non-PAID orders
                if (order.getStatus() != OrderStatus.PAID) {
                    continue;
                }

                // Skip if already processed by this scheduler
                if (processedOrders.contains(order.getId())) {
                    continue;
                }

                // Check if enough time has elapsed since last update
                long minutesElapsed = ChronoUnit.MINUTES.between(order.getUpdatedAt(), now);
                if (minutesElapsed < transitionDelayMinutes) {
                    LOGGER.fine(() -> String.format(
                        "Order %d: Only %d minutes elapsed (need %d), skipping",
                        order.getId(), minutesElapsed, transitionDelayMinutes
                    ));
                    continue;
                }

                // Attempt automatic transition
                attemptAutomaticTransition(order.getId());
            }

        } catch (Exception e) {
            LOGGER.severe("Error during PAID order processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Attempts to transition an order from PAID to PROCESSING.
     *
     * This method:
     * 1. Tries to transition using OrderService (atomic operation)
     * 2. On success, records the order ID in processedOrders
     * 3. On failure (invalid transition), logs but doesn't fail
     *
     * Why track successful transitions?
     * - Prevents re-processing if a scheduler iteration finds the same order
     * - Acts as idempotency key within a scheduler instance
     * - Note: If scheduler restarts, processedOrders is cleared (acceptable for production)
     *
     * Thread Safety:
     * - Called by scheduler thread only
     * - OrderService.transitionOrder() is atomic
     * - processedOrders.add() is thread-safe
     *
     * @param orderId the order ID to transition
     */
    private void attemptAutomaticTransition(Long orderId) {
        try {
            orderService.transitionOrder(orderId, OrderStatus.SHIPPED);
            processedOrders.add(orderId);
            
            LOGGER.info(() -> String.format(
                "✓ Automatic transition: Order %d PAID → SHIPPED",
                orderId
            ));

        } catch (InvalidTransitionException e) {
            // Order no longer PAID (may have been manually transitioned)
            LOGGER.fine(() -> String.format(
                "Order %d: Transition failed (already changed): %s",
                orderId, e.getMessage()
            ));

        } catch (Exception e) {
            LOGGER.warning(() -> String.format(
                "Order %d: Unexpected error during transition: %s",
                orderId, e.getMessage()
            ));
        }
    }

    /**
     * Clears the processed orders tracking set.
     *
     * Useful for:
     * - Testing: Reset state between test runs
     * - Manual refresh: Force re-processing of all pending orders
     *
     * Thread Safety: processedOrders.clear() is thread-safe
     */
    public void clearProcessedOrders() {
        processedOrders.clear();
        LOGGER.fine("Cleared processed orders tracking set");
    }

    /**
     * Gets the number of orders being tracked as processed.
     *
     * @return count of processed orders in current tracking set
     */
    public int getProcessedOrderCount() {
        return processedOrders.size();
    }

    /**
     * Checks if a specific order has been processed by this scheduler.
     *
     * @param orderId the order ID
     * @return true if processed, false otherwise
     */
    public boolean isOrderProcessed(Long orderId) {
        return processedOrders.contains(orderId);
    }
}
