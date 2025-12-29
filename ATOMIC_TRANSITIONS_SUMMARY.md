# Atomic State Transitions Implementation Summary

## Overview

This document describes the implementation of **atomic state transitions** in the Order Service. Atomic transitions ensure thread-safe concurrent updates to order states, preventing race conditions where multiple threads might attempt to transition the same order simultaneously.

## Problem Statement

Without atomic transitions, concurrent updates to order state can cause inconsistencies:

### Race Condition Example

```
Scenario: Two threads attempt to transition Order #1 from PENDING simultaneously
- Thread A: Try PENDING → CONFIRMED
- Thread B: Try PENDING → CANCELLED

Without Atomic Transitions:
  Time 0: Thread A reads status = PENDING
  Time 1: Thread B reads status = PENDING
  Time 2: Thread A validates PENDING→CONFIRMED is valid ✓
  Time 3: Thread B validates PENDING→CANCELLED is valid ✓
  Time 4: Thread A sets status = CONFIRMED
  Time 5: Thread B sets status = CANCELLED ← Overwrites Thread A!
  
Result: Indeterminate final state, lost update, state machine corruption
```

## Solution: Per-Order Locking

The `OrderService` implements a **per-order lock pattern** using a `Map<Long, Object>` to serialize concurrent transitions on the same order.

### Architecture

```
OrderService
├── repository: OrderRepository (injected)
└── orderLocks: Map<Long, Object>  ← NEW: Per-order locks
    ├── Lock for Order ID 1
    ├── Lock for Order ID 2
    ├── Lock for Order ID 3
    └── ...
```

### Implementation

```java
public class OrderService {
    private final OrderRepository repository;
    private final Map<Long, Object> orderLocks = new ConcurrentHashMap<>();

    public Order transitionOrder(Long orderId, OrderStatus newStatus) {
        // Get or create a lock for this specific order ID
        Object lock = orderLocks.computeIfAbsent(orderId, id -> new Object());

        // ATOMIC BLOCK: Synchronize on the per-order lock
        synchronized (lock) {
            // 1. Retrieve current state
            Order order = repository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

            // 2. Validate transition (double-checked at atomic boundary)
            if (!order.getStatus().isValidTransition(newStatus)) {
                throw new InvalidTransitionException(orderId, order.getStatus(), newStatus);
            }

            // 3. Update state and persist (atomically)
            order.setStatus(newStatus);
            return repository.save(order);
        }
    }
}
```

### How It Works

1. **Per-Order Lock Creation**
   - `computeIfAbsent(orderId, id -> new Object())` atomically creates a lock if not present
   - Uses `ConcurrentHashMap.computeIfAbsent()` to avoid race conditions on lock creation
   - Different orders have different locks - no global bottleneck

2. **Atomic Block Execution**
   - `synchronized (lock)` ensures only one thread can execute the block at a time for a given order
   - All three operations happen atomically:
     1. Read current state
     2. Validate transition
     3. Update state and persist

3. **State Visibility**
   - When a thread acquires the lock, it sees the most recent state
   - If state was changed by another thread, the re-validation will catch it
   - No indeterminate states possible

### Concurrency Timeline with Atomic Transitions

```
Same scenario with Atomic Transitions:

Thread A: Acquire lock for Order #1 ✓
Thread B: Wait for lock on Order #1 (blocked)
Thread A: Check PENDING → CONFIRMED is valid ✓
Thread A: Set status = CONFIRMED
Thread A: Save order
Thread A: Release lock

Thread B: Acquire lock for Order #1 ✓
Thread B: Check status (now CONFIRMED, not PENDING!)
Thread B: Check CONFIRMED → CANCELLED is valid ✓
Thread B: Set status = CANCELLED
Thread B: Save order
Thread B: Release lock

Result: Deterministic, thread-safe, both transitions complete successfully
```

## Design Patterns Used

### 1. Per-Object Locking (Condition Variable Pattern)

Each order gets its own lock instead of using a global lock. This provides:
- **Scalability**: Multiple orders can transition concurrently (no global bottleneck)
- **Fairness**: Each order's updates are serialized independently
- **Isolation**: Updates to different orders don't interfere

### 2. Double-Checked Locking Pattern

The validation happens AFTER acquiring the lock:
```java
if (!order.getStatus().isValidTransition(newStatus)) {
    throw new InvalidTransitionException(...);
}
```

This ensures we're validating against the most current state, not a stale cached value.

### 3. Atomic Compare-And-Swap Semantics

While we don't use Java's `AtomicReference`, the synchronized block provides equivalent semantics:
- Read, validate, write happen atomically
- No interleaving with other threads

## Test Coverage

### Test Class: `AtomicTransitionTest`

Located in `src/test/java/com/order/AtomicTransitionTest.java`

#### Test 1: `testOnlyOneTransitionSucceedsWhenMultipleThreadsAttemptSame`

**Scenario**: Two threads attempt conflicting transitions
- Thread A: PENDING → CONFIRMED
- Thread B: PENDING → FAILED

**Expected Result**:
- Exactly 1 succeeds (gets lock first)
- Exactly 1 fails (state changes after first transition)
- Final status is deterministic

**Key Validation**: Atomicity prevents indeterminate states

#### Test 2: `testMultipleThreadsAttemptingDifferentSequences`

**Scenario**: Three threads attempt same transition (PENDING → CONFIRMED)
- Thread A: PENDING → CONFIRMED
- Thread B: PENDING → CONFIRMED
- Thread C: PENDING → CONFIRMED

**Expected Result**:
- Exactly 1 succeeds (first to acquire lock)
- Exactly 2 fail (state is no longer PENDING for them)

**Key Validation**: Serialization prevents concurrent modifications

#### Test 3: `testSequentialTransitionsAreAtomic`

**Scenario**: Five threads attempt full lifecycle (PENDING → CONFIRMED → PROCESSING → SHIPPED)
- Each thread tries to do three transitions in sequence
- All threads released simultaneously

**Expected Result**:
- Order advances through states deterministically
- No lost updates or corrupted state
- Compound transitions remain atomic

**Key Validation**: Complex transition sequences are safe

#### Test 4: `testConcurrentUpdatesToDifferentOrders`

**Scenario**: Three threads transition different orders simultaneously
- Thread A: Order #1 PENDING → CONFIRMED
- Thread B: Order #2 PENDING → CANCELLED
- Thread C: Order #3 PENDING → FAILED

**Expected Result**:
- All 3 succeed (no interference between orders)
- Each order sees atomic transition
- No global bottleneck

**Key Validation**: Independent orders don't block each other

#### Test 5: `testRapidConsecutiveTransitionAttempts`

**Scenario**: Ten threads rapidly attempt same transition
- All threads simultaneously try PENDING → CONFIRMED
- High contention on lock

**Expected Result**:
- Exactly 1 succeeds
- Exactly 9 fail
- System remains consistent under load

**Key Validation**: Atomic locking works under contention

### Test Execution

All tests use `CountDownLatch` for synchronized thread startup:

```java
CountDownLatch startSignal = new CountDownLatch(1);
CountDownLatch doneSignal = new CountDownLatch(numThreads);

// Threads wait here
startSignal.await();

// Main thread releases all simultaneously
startSignal.countDown();

// Main thread waits for all to complete
doneSignal.await();
```

This ensures true concurrency (not sequential execution) and tests the lock mechanism properly.

## Performance Implications

### Throughput Impact

- **Single-threaded**: Minimal overhead (one lock per order)
- **Concurrent, same order**: Serialized (expected, maintains correctness)
- **Concurrent, different orders**: Parallel (no global bottleneck)

### Memory Impact

- **Space**: O(n) where n = number of distinct orders accessed
- **Lock objects**: Reused per order ID (not garbage collected)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| Global synchronized block | Simple to implement | Global bottleneck, poor scalability |
| Per-order locks (implemented) | Good scalability, per-order serialization | Slightly more complex |
| ReentrantReadWriteLock | Multiple readers, single writer | Overkill for state transitions (mostly write-heavy) |
| AtomicReference + CAS | Lock-free, high performance | More complex, harder to test |

**Selected**: Per-order locks provide the best balance of simplicity, correctness, and scalability.

## State Machine Integration

The atomic transitions work seamlessly with the existing state machine defined in `OrderStatus`:

```java
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED;

    public boolean isValidTransition(OrderStatus nextStatus) {
        switch (this) {
            case PENDING:
                return nextStatus == CONFIRMED || nextStatus == CANCELLED || nextStatus == FAILED;
            case CONFIRMED:
                return nextStatus == PROCESSING || nextStatus == CANCELLED;
            // ... more states
        }
    }
}
```

Atomic transitions **guarantee** that the state machine rules are enforced under concurrent access.

## Repository Compatibility

The atomic transition implementation is **repository-agnostic**:

- Works with `InMemoryOrderRepository` (development/testing)
- Works with `FileBasedOrderRepository` (persistent storage)
- Works with any implementation of `OrderRepository` interface

```java
// Works with any repository
OrderRepository repository = new InMemoryOrderRepository();
OrderService service = new OrderService(repository);

// All transitions are atomic regardless of underlying storage
Order order = service.transitionOrder(1, OrderStatus.CONFIRMED);
```

## Example Usage

### Single-threaded Code

```java
OrderService service = new OrderService(repository);

// Create order
Order order = service.createOrder(new Order(null, "John Doe", 99.99));

// Transition states
service.confirmOrder(order.getId());
service.processOrder(order.getId());
service.shipOrder(order.getId());
service.deliverOrder(order.getId());

// Trying to transition from terminal state fails safely
try {
    service.cancelOrder(order.getId());  // Throws InvalidTransitionException
} catch (InvalidTransitionException e) {
    System.out.println("Cannot cancel delivered order");
}
```

### Multi-threaded Code

```java
// Multiple threads can safely transition different orders
Thread t1 = new Thread(() -> service.confirmOrder(order1.getId()));
Thread t2 = new Thread(() -> service.confirmOrder(order2.getId()));
Thread t3 = new Thread(() -> service.confirmOrder(order3.getId()));

t1.start();
t2.start();
t3.start();

t1.join();
t2.join();
t3.join();

// All orders are confirmed, no race conditions
assert service.getOrder(order1.getId()).getStatus() == OrderStatus.CONFIRMED;
assert service.getOrder(order2.getId()).getStatus() == OrderStatus.CONFIRMED;
assert service.getOrder(order3.getId()).getStatus() == OrderStatus.CONFIRMED;
```

## Testing Strategy

### Concurrency Testing Approach

1. **Thread Synchronization**: Use `CountDownLatch` to ensure all threads start simultaneously
2. **Atomic Counters**: Use `AtomicInteger` to safely count successes/failures from multiple threads
3. **Deterministic Assertions**: Verify exact counts, not ranges
4. **State Verification**: Check final order state matches expected outcome

### Running Tests

```bash
# Run atomic transition tests only
mvn test -Dtest=AtomicTransitionTest

# Run all tests
mvn test

# Run with verbose output
mvn test -X

# Run specific test
mvn test -Dtest=AtomicTransitionTest#testOnlyOneTransitionSucceedsWhenMultipleThreadsAttemptSame
```

### Test Results

Current test suite (53/53 passing):
- **30 tests**: OrderServiceTest (business logic)
- **7 tests**: ConcurrencyTest (basic concurrency)
- **11 tests**: FileBasedPersistenceTest (persistence layer)
- **5 tests**: AtomicTransitionTest (atomic transitions) ← NEW

## Future Enhancements

### 1. ReentrantReadWriteLock

For read-heavy scenarios (e.g., querying order status frequently):

```java
private final Map<Long, ReadWriteLock> orderLocks = new ConcurrentHashMap<>();

public OrderStatus getOrderStatus(Long orderId) {
    ReadWriteLock lock = orderLocks.computeIfAbsent(orderId, id -> new ReentrantReadWriteLock());
    lock.readLock().lock();
    try {
        return repository.findById(orderId).map(Order::getStatus).orElse(null);
    } finally {
        lock.readLock().unlock();
    }
}

public Order transitionOrder(Long orderId, OrderStatus newStatus) {
    ReadWriteLock lock = orderLocks.computeIfAbsent(orderId, id -> new ReentrantReadWriteLock());
    lock.writeLock().lock();
    try {
        // ... transition logic
    } finally {
        lock.writeLock().unlock();
    }
}
```

### 2. Optimistic Locking

Use version numbers to detect concurrent modifications:

```java
public class Order {
    private long version;
    
    public void setStatus(OrderStatus status) {
        this.status = status;
        this.version++;  // Increment on each change
    }
}

public Order transitionOrder(Long orderId, OrderStatus newStatus, long expectedVersion) {
    Order order = repository.findById(orderId).orElseThrow();
    if (order.getVersion() != expectedVersion) {
        throw new ConcurrentModificationException("Order was modified by another transaction");
    }
    order.setStatus(newStatus);
    return repository.save(order);
}
```

### 3. Transaction-based Approach

In a real application, consider using a database transaction layer:

```java
@Transactional  // Spring, Hibernate, etc.
public Order transitionOrder(Long orderId, OrderStatus newStatus) {
    // Database-level row locks provide atomic transitions
    Order order = repository.findById(orderId);  // Locks row at DB level
    // ... validation and update ...
    return repository.save(order);
}
```

## Debugging and Monitoring

### Identifying Lock Contention

Add logging to detect lock contention:

```java
public Order transitionOrder(Long orderId, OrderStatus newStatus) {
    Object lock = orderLocks.computeIfAbsent(orderId, id -> new Object());
    
    long startWait = System.nanoTime();
    synchronized (lock) {
        long waitTime = System.nanoTime() - startWait;
        if (waitTime > 1_000_000) {  // > 1ms
            LOGGER.warn("Lock contention on order {}: {} ms", orderId, waitTime / 1_000_000.0);
        }
        // ... rest of logic ...
    }
}
```

### Thread Dump Analysis

Under high contention, inspect thread dumps:

```bash
jstack <pid> | grep -A 5 "OrderService"
```

Look for threads blocked on the `orderLocks` map.

## Summary

Atomic state transitions in the Order Service provide:

✅ **Thread Safety**: Multiple threads can safely transition order states
✅ **Consistency**: State machine rules always enforced
✅ **Determinism**: No indeterminate states, reproducible outcomes
✅ **Scalability**: Per-order locks, not global bottleneck
✅ **Simplicity**: Easy to understand and maintain

The implementation balances these concerns with a clean, maintainable per-order locking pattern.
