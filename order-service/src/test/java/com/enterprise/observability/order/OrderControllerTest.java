package com.enterprise.observability.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OrderServiceApplication.class)
@AutoConfigureMockMvc
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSlowEndpointExecution() throws Exception {
        // Assert slow endpoint returns 200 OK along with simulated database latency values
        mockMvc.perform(get("/orders/slow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.latency").exists());
    }

    @Test
    public void testCreateOrderValidationFailure() throws Exception {
        // Ensure createOrder endpoint handles requests correctly (e.g. failing payment when no real server exists downstream)
        String requestBody = "{\"userId\": \"user-123\", \"amount\": 150.0}";

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
    }
}
