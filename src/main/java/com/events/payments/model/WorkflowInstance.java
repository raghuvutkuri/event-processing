package com.events.payments.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Represents a running instance of a workflow
@Data
@NoArgsConstructor
public class WorkflowInstance {
    private UUID id;
    private String workflowName;
    private WorkflowStatus status;
    private int currentStepIndex;
    // Stores data shared across steps. Using ConcurrentHashMap for thread-safety in this in-memory example.
    private Map<String, Object> transactionContext;
    private Instant createdAt;
    private Instant updatedAt;
    private String traceId; // For distributed tracing (simplified here)

    // List of individual step executions within this workflow instance
    private List<StepExecution> stepExecutions;

    public WorkflowInstance(String workflowName, Map<String, Object> initialContext, String traceId, List<StepDefinition> stepDefinitions) {
        this.id = UUID.randomUUID();
        this.workflowName = workflowName;
        this.status = WorkflowStatus.STARTED;
        this.currentStepIndex = 0;
        this.transactionContext = new ConcurrentHashMap<>(initialContext); // Ensure context is mutable
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.traceId = traceId;

        // Initialize step executions based on workflow definition
        this.stepExecutions = stepDefinitions.stream()
                .map(stepDef -> new StepExecution(stepDef.getName()))
                .toList();
    }

    // Helper to get the current step definition
    public StepDefinition getCurrentStepDefinition(WorkflowDefinition definition) {
        if (currentStepIndex >= 0 && currentStepIndex < definition.getSteps().size()) {
            return definition.getSteps().get(currentStepIndex);
        }
        return null;
    }

    // Helper to get the current step execution
    public StepExecution getCurrentStepExecution() {
        if (currentStepIndex >= 0 && currentStepIndex < stepExecutions.size()) {
            return stepExecutions.get(currentStepIndex);
        }
        return null;
    }
}