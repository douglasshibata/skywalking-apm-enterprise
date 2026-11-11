package com.enterprise.observability.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PaymentServiceApplication.class)
@AutoConfigureMockMvc
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSuccessfulPaymentSimulation() throws Exception {
        String payload = "{\"orderId\": \"order-abc\", \"userId\": \"user-100\", \"amount\": 250.0}";

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Payment processed successfully."));
    }

    @Test
    public void testPaymentRejectionDueToLimitExceeded() throws Exception {
        String payload = "{\"orderId\": \"order-abc\", \"userId\": \"user-100\", \"amount\": 15000.0}";

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Credit limit exceeded. Maximum transaction is $10,000.00."));
    }
}
