package com.order;

import com.order.exception.InvalidTransitionException;
import com.order.model.Order;
import com.order.repository.InMemoryOrderRepository;
import com.order.service.OrderService;

public class Main {
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║        Order Service - State Transition Demo              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // Initialize repository and service
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderService service = new OrderService(repository);

        // ========== Scenario 1: Complete Order Lifecycle ==========
        System.out.println("📦 SCENARIO 1: Complete Order Lifecycle (PENDING → DELIVERED)\n");
        System.out.println("─".repeat(60));
        
        Order order1 = service.createOrder(new Order(null, "Alice Johnson", 249.99));
        System.out.println("✓ Order Created: " + order1);
        printOrderStatus(order1);

        order1 = service.confirmOrder(order1.getId());
        System.out.println("✓ Order Confirmed: " + order1);
        printOrderStatus(order1);

        order1 = service.processOrder(order1.getId());
        System.out.println("✓ Order Processing: " + order1);
        printOrderStatus(order1);

        order1 = service.shipOrder(order1.getId());
        System.out.println("✓ Order Shipped: " + order1);
        printOrderStatus(order1);

        order1 = service.deliverOrder(order1.getId());
        System.out.println("✓ Order Delivered (Terminal State): " + order1);
        printOrderStatus(order1);

        // Try to transition from terminal state (should fail)
        System.out.println("\n⚠ Attempting transition from terminal state DELIVERED → CANCELLED");
        try {
            service.cancelOrder(order1.getId());
            System.out.println("❌ ERROR: Should have thrown InvalidTransitionException!");
        } catch (InvalidTransitionException e) {
            System.out.println("✓ Correctly rejected: " + e.getMessage());
        }

        // ========== Scenario 2: Order Cancellation ==========
        System.out.println("\n\n📦 SCENARIO 2: Order Cancellation (PENDING → CANCELLED)\n");
        System.out.println("─".repeat(60));

        Order order2 = service.createOrder(new Order(null, "Bob Smith", 99.99));
        System.out.println("✓ Order Created: " + order2);
        printOrderStatus(order2);

        order2 = service.cancelOrder(order2.getId());
        System.out.println("✓ Order Cancelled (Terminal State): " + order2);
        printOrderStatus(order2);

        // ========== Scenario 3: Cancelled Order Attempt Transitions ==========
        System.out.println("\n⚠ Attempting transition from CANCELLED (terminal) → CONFIRMED");
        try {
            service.confirmOrder(order2.getId());
            System.out.println("❌ ERROR: Should have thrown InvalidTransitionException!");
        } catch (InvalidTransitionException e) {
            System.out.println("✓ Correctly rejected: " + e.getMessage());
        }

        // ========== Scenario 4: Order Failure ==========
        System.out.println("\n\n📦 SCENARIO 3: Order Failure (PENDING → FAILED)\n");
        System.out.println("─".repeat(60));

        Order order3 = service.createOrder(new Order(null, "Charlie Brown", 150.00));
        System.out.println("✓ Order Created: " + order3);
        printOrderStatus(order3);

        order3 = service.failOrder(order3.getId());
        System.out.println("✓ Order Failed (Terminal State): " + order3);
        printOrderStatus(order3);

        // ========== Scenario 5: Partial Lifecycle ==========
        System.out.println("\n\n📦 SCENARIO 4: Partial Lifecycle (PENDING → CONFIRMED → PROCESSING)\n");
        System.out.println("─".repeat(60));

        Order order4 = service.createOrder(new Order(null, "Diana Prince", 399.99));
        System.out.println("✓ Order Created: " + order4);
        printOrderStatus(order4);

        order4 = service.confirmOrder(order4.getId());
        System.out.println("✓ Order Confirmed: " + order4);
        printOrderStatus(order4);

        order4 = service.processOrder(order4.getId());
        System.out.println("✓ Order Processing: " + order4);
        printOrderStatus(order4);

        // ========== Scenario 6: Invalid State Transition ==========
        System.out.println("\n⚠ Attempting invalid transition: PROCESSING → CONFIRMED (reverse)");
        try {
            service.confirmOrder(order4.getId());
            System.out.println("❌ ERROR: Should have thrown InvalidTransitionException!");
        } catch (InvalidTransitionException e) {
            System.out.println("✓ Correctly rejected: " + e.getMessage());
        }

        // ========== Summary ==========
        System.out.println("\n\n" + "═".repeat(60));
        System.out.println("📊 SUMMARY");
        System.out.println("═".repeat(60));
        System.out.println("✓ Order 1 (Alice):  PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED");
        System.out.println("✓ Order 2 (Bob):    PENDING → CANCELLED");
        System.out.println("✓ Order 3 (Charlie): PENDING → FAILED");
        System.out.println("✓ Order 4 (Diana):  PENDING → CONFIRMED → PROCESSING");
        System.out.println("\n✓ All state transitions validated successfully!");
        System.out.println("✓ Invalid transitions correctly rejected!");
        System.out.println("✓ Terminal states properly protected!");
        System.out.println("\n" + "═".repeat(60));
    }

    /**
     * Helper method to display order status details
     */
    private static void printOrderStatus(Order order) {
        String statusIcon;
        switch (order.getStatus()) {
            case PENDING:
                statusIcon = "⏳";
                break;
            case CONFIRMED:
                statusIcon = "✅";
                break;
            case PROCESSING:
                statusIcon = "⚙️";
                break;
            case SHIPPED:
                statusIcon = "🚚";
                break;
            case DELIVERED:
                statusIcon = "📦";
                break;
            case CANCELLED:
                statusIcon = "❌";
                break;
            case FAILED:
                statusIcon = "⚠️";
                break;
            default:
                statusIcon = "❓";
        }

        System.out.println(String.format("  %s Status: %s (%s) - %s",
                statusIcon,
                order.getStatus().getDisplayName(),
                order.getStatus().getDescription(),
                order.getStatus().isTerminal() ? "[TERMINAL]" : "[ACTIVE]"));
    }
}
