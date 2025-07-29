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
 * Simulates the Resource Service.
 * Listens for requests to provision resources and sends a response.
 */
@Component
@Slf4j
public class ResourceServiceKafkaListener {

    private final Random random = new Random();

    @KafkaListener(topics = "resource-service-requests", groupId = "resource-service-group", containerFactory = "kafkaListenerContainerFactory")
    @SendTo("${spring.kafka.producer.reply-topic}")
    public Map<String, Object> handleResourceProvisioningRequest(
            @Payload Map<String, Object> requestPayload,
            @Header(KafkaHeaders.CORRELATION_ID) byte[] correlationIdBytes) {

        String correlationId = new String(correlationIdBytes);
        String userId = (String) requestPayload.get("userId");

        log.info("Resource Service: Received request to provision resources for user '{}'. CorrelationId: {}", userId, correlationId);

        Map<String, Object> response = new HashMap<>();
        try {
            Thread.sleep(random.nextInt(800) + 200); // Simulate 200-1000ms processing
            if (random.nextInt(10) < 1) { // 10% chance of failure
                throw new RuntimeException("Simulated resource provisioning failure!");
            }

            String resourceId = "res-" + UUID.randomUUID().toString().substring(0, 8);
            response.put("status", "SUCCESS");
            Map<String, Object> data = new HashMap<>();
            data.put("resourceId", resourceId);
            data.put("message", "Resources provisioned successfully.");
            response.put("data", data);
            log.info("Resource Service: Successfully provisioned resources for user {}. CorrelationId: {}", userId, correlationId);

        } catch (Exception e) {
            log.error("Resource Service: Failed to provision resources. CorrelationId: {}. Error: {}", correlationId, e.getMessage());
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }
        return response;
    }
}
