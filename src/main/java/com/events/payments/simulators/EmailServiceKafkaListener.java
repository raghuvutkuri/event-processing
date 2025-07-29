package com.events.payments.simulators;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Simulates the Email Service.
 * Listens for requests to send a welcome email and sends a response.
 */
@Component
@Slf4j
public class EmailServiceKafkaListener {

    private final Random random = new Random();

    @KafkaListener(topics = "email-service-requests", groupId = "email-service-group", containerFactory = "kafkaListenerContainerFactory")
    @SendTo("${spring.kafka.producer.reply-topic}")
    public Map<String, Object> handleEmailCreationRequest(
            @Payload Map<String, Object> requestPayload,
            @Header(KafkaHeaders.CORRELATION_ID) byte[] correlationIdBytes) {

        String correlationId = new String(correlationIdBytes);
        String userId = (String) requestPayload.get("userId");
        String emailAddress = (String) requestPayload.get("emailAddress");

        log.info("Email Service: Received request to send welcome email to user '{}' ({}). CorrelationId: {}", userId, emailAddress, correlationId);

        Map<String, Object> response = new HashMap<>();
        try {
            Thread.sleep(random.nextInt(300) + 50); // Simulate 50-350ms processing
            if (random.nextInt(10) < 1) { // 10% chance of failure
                throw new RuntimeException("Simulated email sending failure!");
            }

            response.put("status", "SUCCESS");
            Map<String, Object> data = new HashMap<>();
            data.put("emailStatus", "SENT");
            data.put("message", "Welcome email sent successfully.");
            response.put("data", data);
            log.info("Email Service: Successfully sent email to user {}. CorrelationId: {}", userId, correlationId);

        } catch (Exception e) {
            log.error("Email Service: Failed to send email. CorrelationId: {}. Error: {}", correlationId, e.getMessage());
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }
        return response;
    }
}
