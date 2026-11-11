package com.enterprise.observability.payment;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    /**
     * Downstream target for order payment processing. Demonstrates context propagation,
     * trace injection/capture, custom tags via toolkit annotations, and error capturing.
     */
    @PostMapping
    @Trace(operationName = "executeExternalPayment")
    @Tags({
        @Tag(key = "userId", value = "arg[0].get('userId')"),
        @Tag(key = "amount", value = "arg[0].get('amount')"),
        @Tag(key = "orderId", value = "arg[0].get('orderId')")
    })
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> paymentPayload) {
        String orderId = (String) paymentPayload.get("orderId");
        String userId = (String) paymentPayload.get("userId");
        Double amount = Double.valueOf(paymentPayload.getOrDefault("amount", 0.0).toString());

        // Extract and display the SkyWalking Trace ID from SDK API (TraceContext.traceId())
        String currentTraceId = TraceContext.traceId();
        log.info("Received payment request. Order: {}, User: {}, Amount: {}, Active Trace ID: {}",
            orderId, userId, amount, currentTraceId);

        // Capture some active span context tags
        ActiveSpan.tag("payment.transaction.id", UUID.randomUUID().toString());

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("traceId", currentTraceId);

        // Simulate credit limit check / business rule exception simulation
        if (amount > 10000.0) {
            String errMsg = "Credit limit exceeded. Maximum transaction is $10,000.00.";
            log.error("Payment rejected for Order {}. Error: {}", orderId, errMsg);

            // Mark trace as error and write the log
            ActiveSpan.error(new IllegalArgumentException(errMsg));
            ActiveSpan.tag("payment.error.code", "LIMIT_EXCEEDED");

            response.put("status", "REJECTED");
            response.put("message", errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Simulating normal processing delay
        simulateDBProcessing();

        response.put("status", "SUCCESS");
        response.put("message", "Payment processed successfully.");
        log.info("Payment approved for Order {}.", orderId);
        return ResponseEntity.ok(response);
    }

    @Trace(operationName = "simulatedDBTransaction")
    private void simulateDBProcessing() {
        try {
            Thread.sleep(50); // Simulate light DB overhead
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ActiveSpan.info("DB transaction committed successfully.");
    }
}
