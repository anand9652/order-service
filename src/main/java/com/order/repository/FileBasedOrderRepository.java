package com.order.repository;

import com.order.model.Order;
import com.order.model.OrderStatus;
import com.order.model.StatusTransition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-based persistent OrderRepository implementation.
 * 
 * Persists orders to JSON format on disk with the following structure:
 * <pre>
 * {
 *   "orders": [
 *     {
 *       "id": 1,
 *       "customer": "Alice",
 *       "total": 99.99,
 *       "status": "PENDING",
 *       "createdAt": "2025-12-28T09:00:00Z",
 *       "updatedAt": "2025-12-28T09:00:00Z"
 *     }
 *   ],
 *   "nextId": 2
 * }
 * </pre>
 * 
 * Features:
 * - Thread-safe concurrent access using ConcurrentHashMap
 * - Automatic ID generation with AtomicLong
 * - JSON serialization/deserialization
 * - Lazy loading on first access
 * - Automatic persistence after each operation
 */
public class FileBasedOrderRepository implements OrderRepository {
    private final Path dataFile;
    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);
    private volatile boolean loaded = false;

    /**
     * Creates a file-based repository with default data file location.
     * Default: ./data/orders.json
     */
    public FileBasedOrderRepository() {
        this(Paths.get("data", "orders.json"));
    }

    /**
     * Creates a file-based repository with specified data file path.
     * 
     * @param dataFile path to JSON data file
     */
    public FileBasedOrderRepository(Path dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Lazy-loads orders from file on first access.
     */
    private synchronized void ensureLoaded() {
        if (loaded) return;

        if (Files.exists(dataFile)) {
            try {
                loadFromFile();
            } catch (IOException e) {
                System.err.println("Failed to load orders from file: " + e.getMessage());
            }
        }
        loaded = true;
    }

    /**
     * Loads orders from JSON file using manual parsing.
     * Uses Java's built-in String utilities to avoid external JSON libraries.
     */
    private void loadFromFile() throws IOException {
        String content = Files.readString(dataFile);
        
        // Find the orders array start
        int ordersStart = content.indexOf("\"orders\"");
        if (ordersStart == -1) {
            return;
        }

        // Find the opening bracket of the orders array
        int arrayStart = content.indexOf("[", ordersStart);
        if (arrayStart == -1) {
            return;
        }

        // Find the closing bracket of the orders array
        int arrayEnd = content.indexOf("]", arrayStart);
        if (arrayEnd == -1) {
            return;
        }

        // Extract the array content
        String arrayContent = content.substring(arrayStart + 1, arrayEnd);

        // Extract individual order objects
        int braceDepth = 0;
        int objectStart = -1;
        
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            
            if (c == '{') {
                if (braceDepth == 0) {
                    objectStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && objectStart != -1) {
                    String orderJson = arrayContent.substring(objectStart, i + 1);
                    parseAndLoadOrder(orderJson);
                    objectStart = -1;
                }
            }
        }

        // Extract nextId if present
        String nextIdStr = extractJsonValue(content, "nextId");
        if (!nextIdStr.isEmpty()) {
            try {
                idSeq.set(Long.parseLong(nextIdStr));
            } catch (NumberFormatException e) {
                // Use default if parsing fails
            }
        }
    }

    /**
     * Parses a JSON object string and creates an Order.
     */
    private void parseAndLoadOrder(String jsonStr) {
        try {
            Long id = Long.parseLong(extractJsonValue(jsonStr, "id"));
            String customer = extractJsonValue(jsonStr, "customer");
            double total = Double.parseDouble(extractJsonValue(jsonStr, "total"));
            String statusStr = extractJsonValue(jsonStr, "status");
            String createdAtStr = extractJsonValue(jsonStr, "createdAt");
            String updatedAtStr = extractJsonValue(jsonStr, "updatedAt");

            OrderStatus status = OrderStatus.valueOf(statusStr);
            Instant createdAt = Instant.parse(createdAtStr);
            Instant updatedAt = Instant.parse(updatedAtStr);

            // Parse statusHistory if available (for backwards compatibility, use empty list if not present)
            List<StatusTransition> statusHistory = new ArrayList<>();
            // TODO: Parse statusHistory from JSON array if it exists
            // For now, we pass empty list and the order will rebuild history on creation

            // Use factory method to restore order with preserved timestamps
            Order order = Order.fromPersistence(id, customer, total, status, createdAt, updatedAt, statusHistory);
            store.put(id, order);

            // Track highest ID for next generation
            if (id >= idSeq.get()) {
                idSeq.set(id + 1);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse order from JSON: " + e.getMessage());
        }
    }

    /**
     * Extracts a JSON field value from a string using simple pattern matching.
     * Handles strings, numbers, and timestamps properly.
     */
    private String extractJsonValue(String json, String field) {
        // Look for "field": (with optional whitespace after colon)
        String prefix = "\"" + field + "\":";
        int start = json.indexOf(prefix);
        if (start == -1) {
            return "";
        }

        start += prefix.length();
        
        // Skip whitespace (spaces, tabs, newlines)
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length()) {
            return "";
        }

        // Extract value based on type
        if (json.charAt(start) == '"') {
            // String value - properly handle escape sequences
            start++;
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            
            while (start < json.length()) {
                char c = json.charAt(start);
                
                if (escaped) {
                    // Handle escape sequences
                    switch (c) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case '/' -> value.append('/');
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            // Unicode escape: \\uXXXX
                            if (start + 4 < json.length()) {
                                String hex = json.substring(start + 1, start + 5);
                                try {
                                    value.append((char) Integer.parseInt(hex, 16));
                                    start += 4;
                                } catch (NumberFormatException e) {
                                    value.append(c);
                                }
                            }
                        }
                        default -> value.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // End of string
                    return value.toString();
                } else {
                    value.append(c);
                }
                start++;
            }
        } else if (json.charAt(start) == '-' || json.charAt(start) == '+' || 
                   Character.isDigit(json.charAt(start))) {
            // Number or timestamp value - capture everything until comma or closing brace
            StringBuilder value = new StringBuilder();
            while (start < json.length()) {
                char c = json.charAt(start);
                // Stop at comma or closing brace (end of this field)
                // Also stop at closing bracket for array values
                if (c == ',' || c == '}' || c == ']') {
                    break;
                }
                value.append(c);
                start++;
            }
            return value.toString().trim();
        } else if (json.charAt(start) == 't' || json.charAt(start) == 'f') {
            // Boolean value
            if (json.startsWith("true", start)) {
                return "true";
            } else if (json.startsWith("false", start)) {
                return "false";
            }
        } else if (json.charAt(start) == 'n') {
            // null value
            if (json.startsWith("null", start)) {
                return "null";
            }
        }

        return "";
    }

    /**
     * Persists all orders to JSON file.
     */
    private void persistToFile() {
        try {
            // Create parent directories if needed
            Files.createDirectories(dataFile.getParent());

            // Build JSON content
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"orders\": [\n");

            var orders = new ArrayList<>(store.values());
            for (int i = 0; i < orders.size(); i++) {
                Order order = orders.get(i);
                json.append(orderToJson(order, "    "));
                if (i < orders.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("  ],\n");
            json.append("  \"nextId\": ").append(idSeq.get()).append("\n");
            json.append("}");

            // Write to file
            Files.writeString(dataFile, json.toString());
        } catch (IOException e) {
            System.err.println("Failed to persist orders to file: " + e.getMessage());
        }
    }

    /**
     * Converts an Order to JSON string format.
     */
    private String orderToJson(Order order, String indent) {
        return String.format("""
            %s{
            %s  "id": %d,
            %s  "customer": "%s",
            %s  "total": %.2f,
            %s  "status": "%s",
            %s  "createdAt": "%s",
            %s  "updatedAt": "%s"
            %s}""",
            indent,
            indent, order.getId(),
            indent, escapeJson(order.getCustomer()),
            indent, order.getTotal(),
            indent, order.getStatus().name(),
            indent, order.getCreatedAt(),
            indent, order.getUpdatedAt(),
            indent);
    }

    /**
     * Escapes JSON string values.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    @Override
    public Order save(Order order) {
        ensureLoaded();
        
        if (order.getId() == null) {
            order.setId(idSeq.getAndIncrement());
        }
        
        store.put(order.getId(), order);
        persistToFile();
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        ensureLoaded();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        ensureLoaded();
        store.remove(id);
        persistToFile();
    }

    @Override
    public List<Order> findAll() {
        ensureLoaded();
        return new ArrayList<>(store.values());
    }

    /**
     * Returns the path to the data file being used.
     */
    public Path getDataFilePath() {
        return dataFile;
    }

    /**
     * Clears all orders and deletes the data file.
     * Useful for testing and resetting state.
     */
    public void clearAll() throws IOException {
        store.clear();
        idSeq.set(1);
        if (Files.exists(dataFile)) {
            Files.delete(dataFile);
        }
        loaded = true;
    }
}
