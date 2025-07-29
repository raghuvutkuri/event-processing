package com.events.payments.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class KafkaRequestReplyService {

    private final ReplyingKafkaTemplate<String, Object, Object> replyingKafkaTemplate;

    public KafkaRequestReplyService(ReplyingKafkaTemplate<String, Object, Object> replyingKafkaTemplate) {
        this.replyingKafkaTemplate = replyingKafkaTemplate;
    }

    /**
     * Sends a request message to a Kafka topic and waits for a reply on a specified response topic.
     *
     * @param requestTopic  The topic to send the request to.
     * @param responseTopic The topic to listen for the reply on.
     * @param correlationId A unique ID to correlate the request and response.
     * @param payload       The message payload.
     * @param timeoutMillis The maximum time to wait for a reply in milliseconds.
     * @return A CompletableFuture that will be completed with the response payload or an exception.
     */
    public CompletableFuture<Object> sendAndReceive(String requestTopic, String responseTopic, String correlationId, Object payload, long timeoutMillis) {
        log.info("Sending Kafka request. Topic: {}, ReplyTo: {}, CorrelationId: {}", requestTopic, responseTopic, correlationId);

        // Build the Kafka message with correlation ID and reply topic in headers
        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, requestTopic)
                .setHeader(KafkaHeaders.REPLY_TOPIC, responseTopic) // Tell the consumer where to reply
                .setHeader(KafkaHeaders.CORRELATION_ID, correlationId.getBytes()) // Crucial for matching
                .build();

        // Create a ProducerRecord from the message
        ProducerRecord<String, Object> record = new ProducerRecord<>(requestTopic, correlationId, payload);
        record.headers().add(KafkaHeaders.REPLY_TOPIC, responseTopic.getBytes());
        record.headers().add(KafkaHeaders.CORRELATION_ID, correlationId.getBytes());


        // Send the request and get a future for the reply
        RequestReplyFuture<String, Object, Object> replyFuture = replyingKafkaTemplate.sendAndReceive(record);

        // Convert ListenableFuture to CompletableFuture for easier async handling
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();

        try {
            SendResult<String, Object> sendResult = replyFuture.getSendFuture().get();
            log.debug("Request message sent successfully to topic {}: {}", sendResult.getProducerRecord().topic(), sendResult.getProducerRecord().value());
        } catch (Exception ex) {
            log.error("Failed to send request message to topic {}: {}", requestTopic, ex.getMessage());
            completableFuture.completeExceptionally(new RuntimeException("Failed to send Kafka request: " + ex.getMessage(), ex));
        }

        // Add a listener for the reply future
        replyFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                if (ex instanceof TimeoutException) {
                    log.error("Timed out waiting for reply for correlationId {}: {}", correlationId, ex.getMessage());
                    completableFuture.completeExceptionally(new TimeoutException("Kafka reply timed out for correlationId: " + correlationId));
                } else {
                    log.error("Error receiving Kafka reply for correlationId {}: {}", correlationId, ex.getMessage());
                    completableFuture.completeExceptionally(new RuntimeException("Error receiving Kafka reply: " + ex.getMessage(), ex));
                }
            } else {
                log.info("Received Kafka reply for correlationId {}: {}", correlationId, result.value());
                completableFuture.complete(result.value());
            }
        });

        return completableFuture;
    }
}
