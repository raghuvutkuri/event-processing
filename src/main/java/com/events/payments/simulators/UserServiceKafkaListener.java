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
import java.util.UUID;

/**
 * Simulates the User Service.
 * Listens for requests to create a user account and sends a response.
 */
@Component
@Slf4j
public class UserServiceKafkaListener {

    private final Random random = new Random();

    @KafkaListener(topics = "user-service-requests", groupId = "user-service-group", containerFactory = "kafkaListenerContainerFactory")
    @SendTo("${spring.kafka.producer.reply-topic}")
    public Map<String, Object> handleUserCreationRequest(
            @Payload Map<String, Object> requestPayload,
            @Header(KafkaHeaders.CORRELATION_ID) byte[] correlationIdBytes) {

        String correlationId = new String(correlationIdBytes);
        String username = (String) requestPayload.get("username");
        String email = (String) requestPayload.get("email");

        log.info("User Service: Received request to create user '{}' ({}). CorrelationId: {}", username, email, correlationId);

        Map<String, Object> response = new HashMap<>();
        // Simulate some processing time and potential failure
        try {
            Thread.sleep(random.nextInt(500) + 100); // Simulate 100-600ms processing
            if (random.nextInt(10) < 1) { // 10% chance of failure
                throw new RuntimeException("Simulated user creation failure!");
            }

            String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            response.put("status", "SUCCESS");
            Map<String, Object> data = new HashMap<>();
            data.put("userId", userId);
            data.put("message", "User account created successfully.");
            response.put("data", data);
            log.info("User Service: Successfully created user {}. CorrelationId: {}", userId, correlationId);

        } catch (Exception e) {
            log.error("User Service: Failed to create user. CorrelationId: {}. Error: {}", correlationId, e.getMessage());
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }
        return response;
    }
}

