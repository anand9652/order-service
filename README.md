# Order Service

A Java-based order processing service built with Maven. Implements a clean layered architecture with model, service, and repository patterns.

## Project Structure

```
order-service-1/
├── src/
│   ├── main/
│   │   ├── java/com/order/
│   │   │   ├── Main.java                          # Entry point
│   │   │   ├── App.java                           # Demo application
│   │   │   ├── model/
│   │   │   │   ├── Order.java                     # Order entity with timestamps
│   │   │   │   └── OrderStatus.java               # Order state enum (7 states)
│   │   │   ├── service/OrderService.java          # Business logic + Stream utilities
│   │   │   ├── repository/
│   │   │   │   ├── OrderRepository.java           # Interface (unified API)
│   │   │   │   ├── InMemoryOrderRepository.java   # Thread-safe in-memory store
│   │   │   │   └── FileBasedOrderRepository.java  # JSON file-based persistence
│   │   │   └── exception/OrderNotFoundException.java
│   │   └── resources/application.properties
│   ├── data/
│   │   └── orders.json                            # Persistent JSON storage (created at runtime)
│   └── test/
│       ├── java/com/order/
│       │   ├── OrderServiceTest.java              # 30 unit tests
│       │   ├── ConcurrencyTest.java               # 7 concurrency tests
│       │   └── FileBasedPersistenceTest.java      # 11 persistence tests
│       └── resources/
├── pom.xml                                         # Maven configuration
├── README.md                                       # This file
└── .gitignore
```

## Technology Stack

- **Language**: Java 17
- **Build Tool**: Maven 3.9.11
- **Testing**: JUnit 5.9.2 (Jupiter API)
- **Architecture**: Layered (Model → Service → Repository)

## Java 17 Modernizations

This project demonstrates best practices and modern features of Java 17:

### 1. **Enhanced Domain Modeling**
- Automatic timestamp tracking with `java.time.Instant`
- Auto-updating `createdAt` and `updatedAt` fields
- Improved null handling with `Objects.requireNonNullElse()`

```java
public class Order {
    private final Instant createdAt;
    private Instant updatedAt;
    
    public Order(Long id, String customer, double total, OrderStatus status) {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = Objects.requireNonNullElse(status, OrderStatus.PENDING);
    }
}
```

### 2. **Optional Best Practices**
- Comprehensive Optional usage for null-safety
- `orElseThrow()` for exception handling
- `ifPresentOrElse()` for conditional logic

```java
// Safe Optional-based operations
repository.findById(id)
    .ifPresentOrElse(
        order -> repository.deleteById(id),
        () -> { throw new OrderNotFoundException(id); }
    );
```

### 3. **Stream-Based Processing**
- `findAll()` method enabling stream operations
- Declarative filtering, mapping, and aggregation
- Functional approach to data queries

```java
// Stream-based reporting utilities
List<Order> completed = repository.findAll().stream()
    .filter(Order::isTerminalState)
    .collect(Collectors.toList());

double total = repository.findAll().stream()
    .filter(order -> order.getStatus() == status)
    .mapToDouble(Order::getTotal)
    .sum();
```

### 4. **Text Blocks (Triple-Quoted Strings)**
- Cleaner multi-line string formatting
- Better readability for documentation

```java
@Override
public String toString() {
    return """
        Order{id=%d, customer='%s', total=%.2f, status=%s, \
        createdAt=%s, updatedAt=%s}""".formatted(
            id, customer, total, status, createdAt, updatedAt);
}
```

### 5. **Improved Concurrency**
- `ConcurrentHashMap` for thread-safe operations
- `AtomicLong` for lock-free ID generation
- Implicit thread-safety in immutable timestamp fields

```java
private final Map<Long, Order> store = new ConcurrentHashMap<>();
private final AtomicLong idSeq = new AtomicLong(1);
```

## Persistence Layer

The application supports **two persistence implementations** with identical interfaces:

### InMemoryOrderRepository (Default)
- **Thread-Safe**: Uses `ConcurrentHashMap` and `AtomicLong`
- **Performance**: O(1) lookups, no I/O overhead
- **Use Case**: Testing, development, ephemeral data
- **Data Durability**: Lost on application restart

### FileBasedOrderRepository (Persistent)
- **Persistent**: Automatically saves orders to JSON file
- **Format**: Human-readable JSON at `./data/orders.json`
- **Thread-Safe**: Uses `ConcurrentHashMap` + file synchronization
- **Use Case**: Production, data durability required
- **Timestamps**: Preserves `createdAt` and `updatedAt` across restarts

**Switching Between Repositories:**
```java
// Development
OrderRepository devRepo = new InMemoryOrderRepository();

// Production
OrderRepository prodRepo = new FileBasedOrderRepository(
    Paths.get("data", "orders.json")
);

// Use either with the same service code
OrderService service = new OrderService(prodRepo);
```

## Atomic State Transitions

The `OrderService` implements **atomic state transitions** to ensure thread-safe concurrent updates to order states. This prevents race conditions where multiple threads might attempt to transition the same order simultaneously.

### The Problem (Race Condition)

Without atomic transitions, concurrent updates can cause inconsistent state:

```
Timeline:
Thread A: Check PENDING → CONFIRMED is valid ✓
Thread B: Check PENDING → CANCELLED is valid ✓
Thread A: Set status = CONFIRMED
Thread B: Set status = CANCELLED  ← Overwrites Thread A's change!
Result: Indeterminate final state, lost update
```

### The Solution (Per-Order Locking)

`OrderService` uses a **per-order lock pattern** (`Map<Long, Object> orderLocks`) to serialize concurrent transitions on the same order:

```java
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
            throw new InvalidTransitionException(...);
        }

        // 3. Update state and persist (atomically)
        order.setStatus(newStatus);
        return repository.save(order);
    }
}
```

### How It Works

1. **Per-Order Lock**: Each order ID gets its own lock object via `computeIfAbsent()`
2. **Atomic Block**: All three operations happen under a single synchronized lock:
   - Read current state
   - Validate transition
   - Update and persist
3. **Serialization**: Only one thread can hold a given order's lock at a time
4. **State Visibility**: When a thread acquires the lock, it sees the most recent state

### Example: Concurrent Transitions

```
Timeline with Atomic Locking:
Thread A: Acquire lock for Order #1 ✓
Thread B: Wait for lock on Order #1
Thread A: Check PENDING → CONFIRMED is valid ✓, Update, Release lock
Thread B: Acquire lock for Order #1 ✓
Thread B: Check CONFIRMED → CANCELLED is valid ✓, Update, Release lock
Result: Deterministic, thread-safe state machine
```

### Test Coverage

The `AtomicTransitionTest` class validates atomic behavior with 5 comprehensive scenarios:

| Test | Scenario | Validates |
|------|----------|-----------|
| `testOnlyOneTransitionSucceedsWhenMultipleThreadsAttemptSame` | 2 threads, conflicting transitions | Only one succeeds, other fails safely |
| `testMultipleThreadsAttemptingDifferentSequences` | 3 threads, same transition attempt | Serialization prevents concurrent updates |
| `testSequentialTransitionsAreAtomic` | 5 threads, full lifecycle sequence | Compound transitions remain atomic |
| `testConcurrentUpdatesToDifferentOrders` | 3 threads, different orders | Independent orders don't interfere |
| `testRapidConsecutiveTransitionAttempts` | 10 threads, rapid-fire attempts | Atomic behavior under high contention |



- Java 17 or higher
- Maven 3.6+

## Getting Started

### Clone or navigate to the project:

```bash
cd /Users/anand/order-service/order-service-1
```

### Build the project:

```bash
mvn clean package
```

### Run tests:

```bash
mvn test
```

### Run the application:

Option 1 - Using Maven exec plugin:
```bash
mvn exec:java -Dexec.mainClass="com.order.Main"
```

Option 2 - Using the JAR:
```bash
java -jar target/order-service-1.0-SNAPSHOT-java17.jar
```

## Demo Output

Running the application demonstrates all key features of the order state machine:

```
╔════════════════════════════════════════════════════════════╗
║        Order Service - State Transition Demo              ║
╚════════════════════════════════════════════════════════════╝

📦 SCENARIO 1: Complete Order Lifecycle (PENDING → DELIVERED)

────────────────────────────────────────────────────────────
✓ Order Created: Order{id=1, customer='Alice Johnson', total=249.99, status=PENDING}
  ⏳ Status: Pending (Order created, awaiting payment) - [ACTIVE]
✓ Order Confirmed: Order{id=1, customer='Alice Johnson', total=249.99, status=CONFIRMED}
  ✅ Status: Confirmed (Payment received, order confirmed) - [ACTIVE]
✓ Order Processing: Order{id=1, customer='Alice Johnson', total=249.99, status=PROCESSING}
  ⚙️ Status: Processing (Order is being processed) - [ACTIVE]
✓ Order Shipped: Order{id=1, customer='Alice Johnson', total=249.99, status=SHIPPED}
  🚚 Status: Shipped (Order has been shipped) - [ACTIVE]
✓ Order Delivered (Terminal State): Order{id=1, customer='Alice Johnson', total=249.99, status=DELIVERED}
  📦 Status: Delivered (Order delivered to customer) - [TERMINAL]

⚠ Attempting transition from terminal state DELIVERED → CANCELLED
✓ Correctly rejected: Invalid state transition for Order 1: cannot transition from Delivered to Cancelled


📦 SCENARIO 2: Order Cancellation (PENDING → CANCELLED)

────────────────────────────────────────────────────────────
✓ Order Created: Order{id=2, customer='Bob Smith', total=99.99, status=PENDING}
  ⏳ Status: Pending (Order created, awaiting payment) - [ACTIVE]
✓ Order Cancelled (Terminal State): Order{id=2, customer='Bob Smith', total=99.99, status=CANCELLED}
  ❌ Status: Cancelled (Order cancelled by customer or system) - [TERMINAL]


📦 SCENARIO 3: Order Failure (PENDING → FAILED)

────────────────────────────────────────────────────────────
✓ Order Created: Order{id=3, customer='Charlie Brown', total=150.0, status=PENDING}
  ⏳ Status: Pending (Order created, awaiting payment) - [ACTIVE]
✓ Order Failed (Terminal State): Order{id=3, customer='Charlie Brown', total=150.0, status=FAILED}
  ⚠️ Status: Failed (Order processing failed) - [TERMINAL]


📦 SCENARIO 4: Partial Lifecycle (PENDING → CONFIRMED → PROCESSING)

────────────────────────────────────────────────────────────
✓ Order Created: Order{id=4, customer='Diana Prince', total=399.99, status=PENDING}
  ⏳ Status: Pending (Order created, awaiting payment) - [ACTIVE]
✓ Order Confirmed: Order{id=4, customer='Diana Prince', total=399.99, status=CONFIRMED}
  ✅ Status: Confirmed (Payment received, order confirmed) - [ACTIVE]
✓ Order Processing: Order{id=4, customer='Diana Prince', total=399.99, status=PROCESSING}
  ⚙️ Status: Processing (Order is being processed) - [ACTIVE]

⚠ Attempting invalid transition: PROCESSING → CONFIRMED (reverse)
✓ Correctly rejected: Invalid state transition for Order 4: cannot transition from Processing to Confirmed


════════════════════════════════════════════════════════════
📊 SUMMARY
════════════════════════════════════════════════════════════
✓ Order 1 (Alice):  PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
✓ Order 2 (Bob):    PENDING → CANCELLED
✓ Order 3 (Charlie): PENDING → FAILED
✓ Order 4 (Diana):  PENDING → CONFIRMED → PROCESSING

✓ All state transitions validated successfully!
✓ Invalid transitions correctly rejected!
✓ Terminal states properly protected!
```

## API Overview

### OrderService

- `createOrder(Order order)` → Creates and returns a new order with auto-generated ID
- `getOrder(Long id)` → Retrieves an order by ID (throws OrderNotFoundException if not found)
- `deleteOrder(Long id)` → Deletes an order by ID (throws OrderNotFoundException if not found)

### Order Model

```java
// Create order (defaults to PENDING status)
Order order = new Order(null, "John Doe", 99.99);

// Fields:
// - id (Long): Auto-generated unique identifier
// - customer (String): Customer name
// - total (double): Order total amount
// - status (OrderStatus): Current order state (default: PENDING)
```

### OrderStatus Enum

A state machine implementation for the order lifecycle:

| Status | Description | Valid Transitions |
|--------|-------------|-------------------|
| **PENDING** | Order created, awaiting payment | CONFIRMED, CANCELLED, FAILED |
| **CONFIRMED** | Payment received, order confirmed | PROCESSING, CANCELLED |
| **PROCESSING** | Order is being processed | SHIPPED, CANCELLED |
| **SHIPPED** | Order has been shipped | DELIVERED |
| **DELIVERED** | Order delivered to customer | _(Terminal)_ |
| **CANCELLED** | Order cancelled | _(Terminal)_ |
| **FAILED** | Order processing failed | _(Terminal)_ |

**State Transition Example:**
```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED ✓
PENDING → CANCELLED (terminal) ✓
PROCESSING → PENDING ✗ (invalid transition)
```

**Usage:**
```java
Order order = new Order(null, "Jane Doe", 150.0);
// order.getStatus() == OrderStatus.PENDING

// Transition to next state
boolean success = order.transitionTo(OrderStatus.CONFIRMED);
if (success) {
    System.out.println("Order confirmed!");
} else {
    System.out.println("Invalid transition!");
}

// Check if terminal
if (order.getStatus().isTerminal()) {
    System.out.println("Order processing complete");
}
```

## Test Cases (37 Total)

**Order CRUD & Equality (9 tests)**:
✅ testCreateOrder — Validates order creation with auto-generated ID  
✅ testCreateMultipleOrders — Ensures unique IDs across orders  
✅ testGetOrder — Retrieves and validates saved order  
✅ testGetOrderNotFound — Exception handling for missing orders  
✅ testDeleteOrder — Deletes order and verifies it's removed  
✅ testDeleteNonExistentOrder — Exception for deleting non-existent order  
✅ testOrderEquality — Validates equals() and hashCode()  
✅ testOrderInequality — Validates inequality of different orders  
✅ testOrderToString — Validates string representation  

**Order State Machine (7 tests)**:
✅ testOrderInitialStatus — Verifies orders start in PENDING state  
✅ testValidStatusTransition — Validates allowed transitions  
✅ testInvalidStatusTransition — Rejects invalid transitions  
✅ testCompleteOrderWorkflow — Full lifecycle: PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED  
✅ testOrderStatusTerminalStates — Validates terminal state detection  
✅ testOrderCancellationFromPending — Tests cancellation flow  
✅ testOrderStatusDisplayInfo — Validates status display names and descriptions  

**Concurrency Tests (13 tests)**:
✅ testInvalidTransitionInService — Invalid state transitions in service layer  
✅ testValidTransitionInService — Valid state transitions in service  
✅ testFullOrderLifecycleInService — Complete workflow through service  
✅ testCannotTransitionFromDelivered — Terminal state protection  
✅ testInvalidTransitionExceptionDetails — Exception context validation  
✅ testCancelOrderAfterConfirmed — Order cancellation workflow  
✅ testConcurrentOrderCreation — 10 threads × 10 orders (IDs unique & consistent)  
✅ testConcurrentOrderRetrieval — 5 threads reading same order (data consistency)  
✅ testConcurrentTransitionsOnSameOrder — Race condition prevention  
✅ testConcurrentSequentialTransitions — Valid workflows under concurrency  
✅ testHighConcurrencyMixedOperations — 100 operations × 20 threads  
✅ testConcurrentAccessToTerminalOrders — Terminal state immutability  
✅ testStressTestRapidConcurrentOperations — 250 operations × 50 threads  

**Java 17 Modernization Tests (8 tests)**:
✅ testOrderTimestampTracking — Automatic timestamp creation with java.time.Instant  
✅ testOrderUpdatedAtChangesOnStatusUpdate — Timestamp updates on state changes  
✅ testIsTerminalStateMethod — Terminal state detection with Optional  
✅ testGetAllOrders — Stream-safe retrieval of all orders  
✅ testGetOrdersByStatus — Stream-based filtering by status  
✅ testGetCompletedOrders — Terminal state collection using streams  
✅ testGetTotalByStatus — Stream aggregation for financial reporting  
✅ testCountByStatus — Stream-based counting operations  

**Test Results**: 48/48 passed ✅
- OrderServiceTest: 30/30 ✅
- ConcurrencyTest: 7/7 ✅  
- FileBasedPersistenceTest: 11/11 ✅

**Concurrency Tests** (7 tests):
✅ testConcurrentOrderCreation — Thread-safe ID generation  
✅ testConcurrentOrderUpdates — ConcurrentHashMap concurrent access  
✅ testConcurrentStatusTransitions — Multiple threads changing order states  
✅ testConcurrentDeletion — Safe concurrent deletion  
✅ testConcurrentFindById — Parallel lookups don't corrupt state  
✅ testAtomicLongIdGeneration — Lock-free ID generation under contention  
✅ testFindAllConcurrency — Safe iteration while modifying  

**File-Based Persistence Tests** (11 tests):
✅ testCreateOrderPersistsToFile — JSON serialization works  
✅ testOrderSurvivesPersistence — Orders reload correctly from JSON  
✅ testMultipleOrdersPersist — Array persistence handles multiple items  
✅ testOrderDeletionPersists — Deletion reflected in JSON  
✅ testStatusTransitionPersists — State changes preserved across restarts  
✅ testTimestampsPreservedOnPersistence — createdAt/updatedAt restored exactly  
✅ testIdGenerationPersistsAcrossRestarts — nextId sequence continues  
✅ testConcurrentWritesSafety — Concurrent access to file repository  
✅ testJsonFormatIsValid — Generated JSON is well-formed  
✅ testClearAllRemovesFile — clearAll() properly removes data file  
✅ testSpecialCharactersInCustomerName — Escape handling in JSON strings  

## Project Packages

| Package | Purpose |
|---------|---------|
| `com.order` | Root package with Main and App classes |
| `com.order.model` | Domain entities (Order with timestamps, OrderStatus) |
| `com.order.service` | Business logic layer with stream utilities |
| `com.order.repository` | Data access layer (InMemory + FileBasedOrderRepository) |
| `com.order.exception` | Custom exceptions |

## Build Artifacts

- **JAR**: `target/order-service-1.0-SNAPSHOT-java17.jar`
- **Main Class**: `com.order.Main`
- **Compiled Classes**: `target/classes`
- **Test Classes**: `target/test-classes`
- **Data Directory**: `data/orders.json` (created at runtime for persistence)

## Dependencies

- JUnit 5.9.2 Jupiter API (test scope)

## Version History

### v1.2.0 (Current) - File-Based Persistence
- ✅ Added FileBasedOrderRepository for JSON-based persistence
- ✅ Implemented Order.fromPersistence() factory method for timestamp restoration
- ✅ 11 new persistence tests covering all scenarios
- ✅ Dual-repository support with interface-based switching

### v1.1.0 - Java 17 Full Modernization
- ✅ Upgraded from Java 8 to Java 17
- ✅ Migrated from JUnit 4 to JUnit 5 (Jupiter API)
- ✅ Updated Maven compiler plugins for Java 17 compatibility
- ✅ Added 13 comprehensive concurrency tests
- ✅ **NEW**: Added 8 Java 17 modernization tests
- ✅ **NEW**: Implemented java.time.Instant timestamps (createdAt, updatedAt)
- ✅ **NEW**: Improved Optional usage with ifPresentOrElse() and orElseThrow()
- ✅ **NEW**: Added stream-based utility methods (findAll, getByStatus, etc.)
- ✅ **NEW**: Implemented text blocks for cleaner string formatting
- ✅ All 37 tests passing (22 unit + 7 concurrency + 8 Java 17 feature)

### v1.0.0 - Initial Release
- Java 8 with JUnit 4
- 16 unit tests covering CRUD operations and state machine
- Layered architecture (Model → Service → Repository)

## Next Steps

- Add Spring Boot for REST endpoints
- Implement database persistence (replace in-memory store)
- Add logging with SLF4J and Log4j
- Add order state event listeners
- Implement order history/audit trail
- Add API documentation (Swagger/OpenAPI)
- Add integration tests with test containers

## Author

Anand

## License

MIT
