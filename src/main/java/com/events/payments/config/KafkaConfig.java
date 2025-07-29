package com.events.payments.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    /**
     * Producer Factory for sending messages.
     * Uses String for key and JSON for value.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template for sending messages.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Consumer Factory for receiving messages.
     * Uses String for key and JSON for value.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.orchestrator.model, com.example.orchestrator.dto, java.util.Map"); // Important for JSON deserialization
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener Container Factory for general Kafka consumers.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setRecordMessageConverter(new StringJsonMessageConverter()); // Use JSON converter
        return factory;
    }

    /**
     * Replying Kafka Template for request-reply pattern.
     * It uses its own dedicated listener container for replies.
     */
    @Bean
    public ReplyingKafkaTemplate<String, Object, Object> replyingKafkaTemplate(
            ProducerFactory<String, Object> pf,
            ConcurrentMessageListenerContainer<String, Object> repliesContainer) {
        ReplyingKafkaTemplate<String, Object, Object> template = new ReplyingKafkaTemplate<>(pf, repliesContainer);
        template.setDefaultReplyTimeout(Duration.ofSeconds(60)); // Default timeout for replies
        return template;
    }

    /**
     * Listener Container for replies from microservices.
     * This container is used by ReplyingKafkaTemplate to listen for responses.
     * It must be configured with a unique group ID to avoid conflicts with other consumers.
     * The reply topic is dynamically set by the ReplyingKafkaTemplate in the message headers.
     */
    @Bean
    public ConcurrentMessageListenerContainer<String, Object> repliesContainer(
            ConsumerFactory<String, Object> cf) {
        // This container will listen on a generic reply topic (or dynamically assigned by template)
        // Spring Kafka's ReplyingKafkaTemplate handles the actual topic subscription.
        ConcurrentMessageListenerContainer<String, Object> container =
                new ConcurrentMessageListenerContainer<>(cf, new ContainerProperties("orchestrator-replies-internal")); // A dummy topic, actual reply topic is set in headers
        container.getContainerProperties().setGroupId(consumerGroupId + "-replies"); // Unique group ID for replies
        container.getContainerProperties().setMissingTopicsFatal(false); // Don't fail if topic doesn't exist initially
        return container;
    }


    // --- Topic Creation Beans (Optional, for local development convenience) ---
    // In a production environment, topics are usually managed by operations/IaC.

    @Bean
    public NewTopic userServiceRequestsTopic() {
        return new NewTopic("user-service-requests", 1, (short) 1);
    }

    @Bean
    public NewTopic userServiceResponsesTopic() {
        return new NewTopic("user-service-responses", 1, (short) 1);
    }

    @Bean
    public NewTopic emailServiceRequestsTopic() {
        return new NewTopic("email-service-requests", 1, (short) 1);
    }

    @Bean
    public NewTopic emailServiceResponsesTopic() {
        return new NewTopic("email-service-responses", 1, (short) 1);
    }

    @Bean
    public NewTopic resourceServiceRequestsTopic() {
        return new NewTopic("resource-service-requests", 1, (short) 1);
    }

    @Bean
    public NewTopic resourceServiceResponsesTopic() {
        return new NewTopic("resource-service-responses", 1, (short) 1);
    }

    @Bean
    public NewTopic inventoryServiceRequestsTopic() {
        return new NewTopic("inventory-service-requests", 1, (short) 1);
    }

    @Bean
    public NewTopic inventoryServiceResponsesTopic() {
        return new NewTopic("inventory-service-responses", 1, (short) 1);
    }

    @Bean
    public NewTopic paymentServiceRequestsTopic() {
        return new NewTopic("payment-service-requests", 1, (short) 1);
    }

    @Bean
    public NewTopic paymentServiceResponsesTopic() {
        return new NewTopic("payment-service-responses", 1, (short) 1);
    }
}
