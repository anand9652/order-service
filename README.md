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
│   │   │   │   ├── Order.java                     # Order entity
│   │   │   │   └── OrderStatus.java               # Order state enum
│   │   │   ├── service/OrderService.java          # Business logic
│   │   │   ├── repository/
│   │   │   │   ├── OrderRepository.java           # Interface
│   │   │   │   └── InMemoryOrderRepository.java   # Implementation
│   │   │   └── exception/OrderNotFoundException.java
│   │   └── resources/application.properties
│   └── test/
│       ├── java/com/order/
│       │   ├── OrderServiceTest.java              # Unit tests
│       │   └── AppTest.java
│       └── resources/
├── pom.xml                                         # Maven configuration
├── README.md                                       # This file
└── .gitignore
```

## Technology Stack

- **Language**: Java 8
- **Build Tool**: Maven 3.9.11
- **Testing**: JUnit 4.13.2
- **Architecture**: Layered (Model → Service → Repository)

## Prerequisites

- Java 8 or higher
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
java -jar target/order-service-1.0-SNAPSHOT-java8.jar
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

## Test Cases (16 Total)

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

**Test Results**: 16/16 passed ✅

## Project Packages

| Package | Purpose |
|---------|---------|
| `com.order` | Root package with Main and App classes |
| `com.order.model` | Domain entities (Order, OrderStatus) |
| `com.order.service` | Business logic layer |
| `com.order.repository` | Data access layer |
| `com.order.exception` | Custom exceptions |

## Build Artifacts

- **JAR**: `target/order-service-1.0-SNAPSHOT-java8.jar`
- **Main Class**: `com.order.Main`
- **Compiled Classes**: `target/classes`
- **Test Classes**: `target/test-classes`

## Dependencies

- JUnit 4.13.2 (test scope)

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
