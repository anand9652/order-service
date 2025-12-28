package com.order;

import com.order.model.Order;
import com.order.repository.InMemoryOrderRepository;
import com.order.service.OrderService;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Order Service Application ===\n");

        // Initialize repository and service
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderService service = new OrderService(repository);

        // Create sample order
        Order order = new Order(null, "John Doe", 99.99);
        Order createdOrder = service.createOrder(order);
        System.out.println("Created: " + createdOrder);

        // Retrieve order
        Order retrieved = service.getOrder(createdOrder.getId());
        System.out.println("Retrieved: " + retrieved);

        // List orders (demo)
        System.out.println("\n✓ Order Service is running");
    }
}
