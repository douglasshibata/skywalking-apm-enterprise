package com.enterprise.observability.order;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${payment.service.url:http://localhost:8082}")
    private String paymentServiceUrl;

    /**
     * Endpoint for creating an order. Demonstrates auto-instrumentation, manual tag addition,
     * trace context retrieval, and remote HTTP calling which automatically propagates the sw8 headers.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> orderPayload) {
        String orderId = UUID.randomUUID().toString();
        String userId = (String) orderPayload.getOrDefault("userId", "anonymous");
        Double amount = Double.valueOf(orderPayload.getOrDefault("amount", 0.0).toString());

        log.info("Processing order creation. OrderID: {}, UserID: {}, Amount: {}", orderId, userId, amount);

        // Capture active span metadata dynamically via apm-toolkit API
        ActiveSpan.tag("order.id", orderId);
        ActiveSpan.tag("user.id", userId);
        ActiveSpan.tag("order.amount", String.valueOf(amount));

        // Perform business logic wrapped with manual trace method
        boolean paymentApproved = processPaymentRequest(orderId, userId, amount);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("userId", userId);
        response.put("amount", amount);

        if (paymentApproved) {
            response.put("status", "CONFIRMED");
            response.put("message", "Order successfully created and payment processed.");
            log.info("Order CONFIRMED. OrderID: {}", orderId);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "PAYMENT_FAILED");
            response.put("message", "Payment processing failed. Order cannot be completed.");
            log.error("Order PAYMENT_FAILED. OrderID: {}", orderId);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }
    }

    /**
     * Demonstrates annotation-based manual tracing and tagging via @Trace and @Tags.
     * The values are dynamically populated from arguments or returned objects.
     */
    @Trace(operationName = "processPaymentRequestInternal")
    @Tags({
        @Tag(key = "payment.orderId", value = "arg[0]"),
        @Tag(key = "payment.userId", value = "arg[1]"),
        @Tag(key = "payment.amount", value = "arg[2]")
    })
    private boolean processPaymentRequest(String orderId, String userId, Double amount) {
        log.info("Initiating external payment request to {}/payments", paymentServiceUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build request body for Payment Service
        Map<String, Object> paymentPayload = new HashMap<>();
        paymentPayload.put("orderId", orderId);
        paymentPayload.put("userId", userId);
        paymentPayload.put("amount", amount);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(paymentPayload, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                paymentServiceUrl + "/payments",
                request,
                Map.class
            );

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = responseEntity.getBody();
                if (body != null && "SUCCESS".equals(body.get("status"))) {
                    ActiveSpan.info("Payment approved successfully via downstream service.");
                    return true;
                }
            }
            ActiveSpan.error("Payment rejected by downstream service.");
            return false;
        } catch (Exception e) {
            log.error("Exception occurred while communicating with Payment Service", e);
            // Capturing exception on trace span manually using apm-toolkit SDK
            ActiveSpan.error(e);
            ActiveSpan.tag("error.message", e.getMessage());
            return false;
        }
    }

    /**
     * A deliberate slow endpoint to demonstrate SkyWalking's capability to capture latency spikes (P99 metric)
     */
    @GetMapping("/slow")
    @Trace(operationName = "simulateSlowDatabaseQuery")
    public ResponseEntity<Map<String, String>> simulateSlowQuery() {
        log.info("Simulating database slow query...");
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(1500); // 1.5 seconds delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long duration = System.currentTimeMillis() - start;
        ActiveSpan.tag("db.query.duration", duration + "ms");
        log.info("Database slow query completed in {}ms", duration);

        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("latency", duration + "ms");
        return ResponseEntity.ok(response);
    }
}
