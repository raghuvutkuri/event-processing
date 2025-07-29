package com.events.payments.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Represents the execution state of a single step within a workflow instance
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepExecution {
    private String name;
    private WorkflowStatus status;
    private Instant startTime;
    private Instant endTime;
    private int retryCount;
    private String errorMessage;
    private String correlationId; // To link Kafka request/response

    public StepExecution(String name) {
        this.name = name;
        this.status = WorkflowStatus.PENDING;
        this.retryCount = 0;
    }
}