package com.order;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.repository.FileBasedOrderRepository;
import com.order.repository.OrderRepository;
import com.order.service.OrderService;

/**
 * Test suite for file-based persistent order repository.
 * Validates JSON persistence, concurrent access, and data durability.
 */
public class FileBasedPersistenceTest {

    private FileBasedOrderRepository repository;
    private OrderService service;
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        Path dataFile = tempDir.resolve("orders.json");
        repository = new FileBasedOrderRepository(dataFile);
        service = new OrderService(repository);
    }

    @Test
    void testCreateOrderPersistsToFile() throws IOException {
        Order order = new Order(null, "Alice", 100.0);
        service.createOrder(order);

        assertTrue(Files.exists(repository.getDataFilePath()));
        String content = Files.readString(repository.getDataFilePath());
        assertTrue(content.contains("\"customer\": \"Alice\""));
        assertTrue(content.contains("\"total\": 100.00"));
    }

    @Test
    void testOrderSurvivesPersistence() throws IOException {
        Order order = new Order(null, "Bob", 200.0);
        Order created = service.createOrder(order);
        Long id = created.getId();

        // Verify order exists
        Order retrieved = service.getOrder(id);
        assertEquals("Bob", retrieved.getCustomer());
        assertEquals(200.0, retrieved.getTotal(), 0.01);

        // Create new repository from same file (simulates restart)
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        // Order should still exist
        Order restored = newService.getOrder(id);
        assertEquals("Bob", restored.getCustomer());
        assertEquals(200.0, restored.getTotal(), 0.01);
    }

    @Test
    void testMultipleOrdersPersist() throws IOException {
        service.createOrder(new Order(null, "Order1", 100.0));
        service.createOrder(new Order(null, "Order2", 200.0));
        service.createOrder(new Order(null, "Order3", 300.0));

        String content = Files.readString(repository.getDataFilePath());
        assertTrue(content.contains("\"customer\": \"Order1\""));
        assertTrue(content.contains("\"customer\": \"Order2\""));
        assertTrue(content.contains("\"customer\": \"Order3\""));
    }

    @Test
    void testOrderDeletionPersists() throws IOException {
        Order order = new Order(null, "ToDelete", 100.0);
        Order created = service.createOrder(order);
        Long id = created.getId();

        // Verify it exists
        assertTrue(service.getOrder(id) != null);

        // Delete it
        service.deleteOrder(id);

        // Create new repository from same file
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        // Order should not exist
        assertThrows(com.order.exception.OrderNotFoundException.class, () -> {
            newService.getOrder(id);
        });
    }

    @Test
    void testStatusTransitionPersists() throws IOException {
        Order order = new Order(null, "StatusTest", 150.0);
        Order created = service.createOrder(order);
        Long id = created.getId();

        service.payOrder(id);
        service.shipOrder(id);

        // Reload from file
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        Order restored = newService.getOrder(id);
        assertEquals(OrderStatus.SHIPPED, restored.getStatus());
    }

    @Test
    void testTimestampsPreservedOnPersistence() throws IOException {
        Order order = new Order(null, "TimestampTest", 100.0);
        Order created = service.createOrder(order);

        var createdAtOriginal = created.getCreatedAt();
        var updatedAtOriginal = created.getUpdatedAt();

        // Reload from file
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        Order restored = newService.getOrder(created.getId());
        assertEquals(createdAtOriginal, restored.getCreatedAt());
        assertEquals(updatedAtOriginal, restored.getUpdatedAt());
    }

    @Test
    void testIdGenerationPersistsAcrossRestarts() throws IOException {
        Order order1 = service.createOrder(new Order(null, "Order1", 100.0));
        Long id1 = order1.getId();

        // Create new repository from same file
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        // New order should get next ID
        Order order2 = newService.createOrder(new Order(null, "Order2", 200.0));
        Long id2 = order2.getId();

        assertTrue(id2 > id1, "New ID should be greater than previous");
    }

    @Test
    void testConcurrentWritesSafety() throws InterruptedException {
        int numThreads = 5;
        int ordersPerThread = 10;

        var threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < ordersPerThread; j++) {
                    service.createOrder(new Order(null,
                        "Order_T" + threadNum + "_" + j, 100.0 + j));
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All orders should be persisted
        List<Order> allOrders = service.getAllOrders();
        assertEquals(numThreads * ordersPerThread, allOrders.size());
    }

    @Test
    void testJsonFormatIsValid() throws IOException {
        service.createOrder(new Order(null, "FormatTest", 99.99));

        String content = Files.readString(repository.getDataFilePath());

        // Verify basic JSON structure
        assertTrue(content.contains("\"orders\": ["));
        assertTrue(content.contains("\"id\":"));
        assertTrue(content.contains("\"customer\":"));
        assertTrue(content.contains("\"total\":"));
        assertTrue(content.contains("\"status\":"));
        assertTrue(content.contains("\"createdAt\":"));
        assertTrue(content.contains("\"updatedAt\":"));
        assertTrue(content.contains("\"nextId\":"));
    }

    @Test
    void testClearAllRemovesFile() throws IOException {
        service.createOrder(new Order(null, "ToClear", 100.0));
        assertTrue(Files.exists(repository.getDataFilePath()));

        repository.clearAll();
        assertFalse(Files.exists(repository.getDataFilePath()));

        // Service should work with empty state
        List<Order> orders = service.getAllOrders();
        assertEquals(0, orders.size());

        // Can create new orders
        Order newOrder = service.createOrder(new Order(null, "After Clear", 200.0));
        assertEquals(1L, newOrder.getId());
    }

    @Test
    void testSpecialCharactersInCustomerName() throws IOException {
        Order order = new Order(null, "Alice \"The Great\" O'Brien", 100.0);
        Order created = service.createOrder(order);

        // Reload from file
        OrderRepository newRepository = new FileBasedOrderRepository(repository.getDataFilePath());
        OrderService newService = new OrderService(newRepository);

        Order restored = newService.getOrder(created.getId());
        assertEquals("Alice \"The Great\" O'Brien", restored.getCustomer());
    }
}
