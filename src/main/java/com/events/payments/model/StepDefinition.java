package com.events.payments.model;

// Represents a single step definition in a workflow

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {
    private String name;
    private String requestTopic;
    private String responseTopic;
    private String payloadTemplate; // Template for dynamic payload
    // You could add timeout, retry settings, compensation step name here
}
