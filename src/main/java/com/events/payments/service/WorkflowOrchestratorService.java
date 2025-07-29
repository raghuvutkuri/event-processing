package com.events.payments.service;


import com.events.payments.config.WorkflowConfig;
import com.events.payments.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WorkflowOrchestratorService {

    private final WorkflowConfig workflowConfig;
    private final KafkaRequestReplyService kafkaRequestReplyService;
    private final ObjectMapper objectMapper;
    private final Handlebars handlebars;

    // In-memory store for workflow instances (replace with a database in production)
    private final Map<UUID, WorkflowInstance> workflowInstances = new ConcurrentHashMap<>();

    public WorkflowOrchestratorService(WorkflowConfig workflowConfig, KafkaRequestReplyService kafkaRequestReplyService, ObjectMapper objectMapper) {
        this.workflowConfig = workflowConfig;
        this.kafkaRequestReplyService = kafkaRequestReplyService;
        this.objectMapper = objectMapper;
        this.handlebars = new Handlebars(); // For payload templating
    }

    /**
     * Starts a new workflow instance.
     *
     * @param workflowName   The name of the workflow to start.
     * @param initialContext Initial data for the transaction context.
     * @return The ID of the newly created workflow instance.
     */
    public UUID startWorkflow(String workflowName, Map<String, Object> initialContext) {
        WorkflowDefinition workflowDefinition = workflowConfig.getWorkflows().get(workflowName);
        if (workflowDefinition == null) {
            throw new IllegalArgumentException("Workflow definition not found for name: " + workflowName);
        }

        String traceId = UUID.randomUUID().toString(); // Simulate trace ID
        WorkflowInstance instance = new WorkflowInstance(workflowName, initialContext, traceId, workflowDefinition.getSteps());
        workflowInstances.put(instance.getId(), instance);
        log.info("Started workflow instance {}: {}", instance.getId(), workflowName);

        // Asynchronously execute the first step
        executeNextStep(instance.getId());

        return instance.getId();
    }

    /**
     * Executes the next step in a workflow instance.
     * This method is called internally after a step completes or when the workflow starts.
     *
     * @param instanceId The ID of the workflow instance.
     */
    private void executeNextStep(UUID instanceId) {
        WorkflowInstance instance = workflowInstances.get(instanceId);
        if (instance == null) {
            log.warn("Attempted to execute next step for non-existent workflow instance: {}", instanceId);
            return;
        }

        WorkflowDefinition definition = workflowConfig.getWorkflows().get(instance.getWorkflowName());
        if (definition == null) {
            log.error("Workflow definition not found for instance {}. Marking as FAILED.", instanceId);
            updateWorkflowStatus(instanceId, WorkflowStatus.FAILED, "Workflow definition missing.");
            return;
        }

        if (instance.getStatus() == WorkflowStatus.COMPLETED || instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.CANCELED) {
            log.info("Workflow instance {} is already in a terminal state ({}). Not executing next step.", instanceId, instance.getStatus());
            return;
        }

        if (instance.getCurrentStepIndex() >= definition.getSteps().size()) {
            log.info("Workflow instance {} completed all steps.", instanceId);
            updateWorkflowStatus(instanceId, WorkflowStatus.COMPLETED, null);
            return;
        }

        StepDefinition currentStepDef = definition.getSteps().get(instance.getCurrentStepIndex());
        StepExecution currentStepExec = instance.getCurrentStepExecution(); // Get the StepExecution object

        if (currentStepExec == null) {
            log.error("Step execution object not found for step index {} in workflow instance {}. Marking as FAILED.", instance.getCurrentStepIndex(), instanceId);
            updateWorkflowStatus(instanceId, WorkflowStatus.FAILED, "Internal error: Step execution object missing.");
            return;
        }

        currentStepExec.setStatus(WorkflowStatus.STARTED);
        currentStepExec.setStartTime(Instant.now());
        String correlationId = UUID.randomUUID().toString();
        currentStepExec.setCorrelationId(correlationId); // Store correlation ID for tracking

        updateWorkflowStatus(instanceId, WorkflowStatus.IN_PROGRESS, null); // Update overall workflow status

        try {
            // Prepare payload using Handlebars for templating
            Template template = handlebars.compileInline(currentStepDef.getPayloadTemplate());
            // Create a context map for Handlebars, including the transactionContext
            Map<String, Object> templateContext = new ConcurrentHashMap<>();
            templateContext.put("transactionContext", instance.getTransactionContext());
            String jsonPayload = template.apply(templateContext);

            // Convert JSON string to Map<String, Object> for Kafka payload
            Map<String, Object> payloadMap = objectMapper.readValue(jsonPayload, Map.class);

            log.info("Executing step '{}' for workflow instance {}. CorrelationId: {}. Sending to topic: {}",
                    currentStepDef.getName(), instanceId, correlationId, currentStepDef.getRequestTopic());

            // Send Kafka request and register a callback for the reply
            kafkaRequestReplyService.sendAndReceive(
                            currentStepDef.getRequestTopic(),
                            currentStepDef.getResponseTopic(),
                            correlationId,
                            payloadMap,
                            60000L // 60 seconds timeout for Kafka reply
                    )
                    .whenComplete((responsePayload, ex) -> {
                        if (ex != null) {
                            handleStepFailure(instanceId, correlationId, "Kafka communication error: " + ex.getMessage());
                        } else {
                            // Convert responsePayload to Map<String, Object> if it's not already
                            Map<String, Object> responseMap = null;
                            if (responsePayload instanceof Map) {
                                responseMap = (Map<String, Object>) responsePayload;
                            } else if (responsePayload instanceof String) {
                                try {
                                    responseMap = objectMapper.readValue((String) responsePayload, Map.class);
                                } catch (JsonProcessingException jsonEx) {
                                    log.error("Failed to parse Kafka response payload for correlationId {}: {}", correlationId, jsonEx.getMessage());
                                    handleStepFailure(instanceId, correlationId, "Invalid response payload format.");
                                    return;
                                }
                            } else {
                                log.warn("Unexpected Kafka response payload type for correlationId {}: {}", correlationId, responsePayload.getClass().getName());
                                responseMap = Collections.singletonMap("response", responsePayload); // Wrap in a map
                            }

                            if (responseMap != null && "SUCCESS".equals(responseMap.get("status"))) {
                                handleStepCompletion(instanceId, correlationId, responseMap.get("data"));
                            } else {
                                String errorMessage = (responseMap != null && responseMap.containsKey("error")) ? responseMap.get("error").toString() : "Unknown error from microservice.";
                                handleStepFailure(instanceId, correlationId, errorMessage);
                            }
                        }
                    });

        } catch (IOException e) {
            log.error("Error preparing payload for step '{}' in workflow instance {}: {}",
                    currentStepDef.getName(), instanceId, e.getMessage());
            handleStepFailure(instanceId, correlationId, "Payload templating error: " + e.getMessage());
        }
    }

    /**
     * Handles the completion of a step.
     *
     * @param instanceId    The ID of the workflow instance.
     * @param correlationId The correlation ID of the completed step.
     * @param stepOutput    The output data from the completed step.
     */
    public void handleStepCompletion(UUID instanceId, String correlationId, Object stepOutput) {
        WorkflowInstance instance = workflowInstances.get(instanceId);
        if (instance == null) {
            log.warn("Received completion for non-existent workflow instance: {}", instanceId);
            return;
        }

        StepExecution completedStepExec = instance.getStepExecutions().stream()
                .filter(se -> correlationId.equals(se.getCorrelationId()))
                .findFirst()
                .orElse(null);

        if (completedStepExec == null) {
            log.warn("Received completion for unknown correlationId {} in workflow instance {}. Ignoring.", correlationId, instanceId);
            return;
        }

        completedStepExec.setStatus(WorkflowStatus.COMPLETED);
        completedStepExec.setEndTime(Instant.now());
        log.info("Step '{}' completed for workflow instance {}. CorrelationId: {}",
                completedStepExec.getName(), instanceId, correlationId);

        // Update transaction context with output from the completed step
        if (stepOutput instanceof Map) {
            instance.getTransactionContext().putAll((Map<String, Object>) stepOutput);
            log.debug("Updated transaction context for instance {}: {}", instanceId, instance.getTransactionContext());
        } else if (stepOutput != null) {
            // If output is not a map, store it under a generic key or log a warning
            instance.getTransactionContext().put(completedStepExec.getName() + "_output", stepOutput);
            log.warn("Step output for '{}' is not a Map. Stored under key '{}_output'.", completedStepExec.getName(), completedStepExec.getName());
        }


        // Move to the next step
        instance.setCurrentStepIndex(instance.getCurrentStepIndex() + 1);
        instance.setUpdatedAt(Instant.now());
        executeNextStep(instanceId); // Continue the workflow
    }

    /**
     * Handles the failure of a step.
     *
     * @param instanceId    The ID of the workflow instance.
     * @param correlationId The correlation ID of the failed step.
     * @param errorMessage  The error message.
     */
    public void handleStepFailure(UUID instanceId, String correlationId, String errorMessage) {
        WorkflowInstance instance = workflowInstances.get(instanceId);
        if (instance == null) {
            log.warn("Received failure for non-existent workflow instance: {}", instanceId);
            return;
        }

        StepExecution failedStepExec = instance.getStepExecutions().stream()
                .filter(se -> correlationId.equals(se.getCorrelationId()))
                .findFirst()
                .orElse(null);

        if (failedStepExec == null) {
            log.warn("Received failure for unknown correlationId {} in workflow instance {}. Ignoring.", correlationId, instanceId);
            return;
        }

        failedStepExec.setRetryCount(failedStepExec.getRetryCount() + 1);
        failedStepExec.setErrorMessage(errorMessage);
        failedStepExec.setEndTime(Instant.now());

        // For simplicity, we'll mark the workflow as FAILED after the first failure.
        // In a real system, you'd implement retry logic here (e.g., reschedule the step,
        // or move to a Dead Letter Queue after max retries).
        failedStepExec.setStatus(WorkflowStatus.FAILED);
        log.error("Step '{}' failed for workflow instance {}. CorrelationId: {}. Error: {}",
                failedStepExec.getName(), instanceId, correlationId, errorMessage);

        updateWorkflowStatus(instanceId, WorkflowStatus.FAILED, "Step '" + failedStepExec.getName() + "' failed: " + errorMessage);
    }

    /**
     * Updates the overall status of a workflow instance.
     *
     * @param instanceId The ID of the workflow instance.
     * @param newStatus  The new status.
     * @param reason     An optional reason for the status change.
     */
    private void updateWorkflowStatus(UUID instanceId, WorkflowStatus newStatus, String reason) {
        WorkflowInstance instance = workflowInstances.get(instanceId);
        if (instance != null) {
            instance.setStatus(newStatus);
            instance.setUpdatedAt(Instant.now());
            log.info("Workflow instance {} status changed to {}. Reason: {}", instanceId, newStatus, reason != null ? reason : "N/A");
        }
    }

    /**
     * Retrieves the status of a workflow instance.
     *
     * @param instanceId The ID of the workflow instance.
     * @return The WorkflowInstance object, or null if not found.
     */
    public WorkflowInstance getWorkflowStatus(UUID instanceId) {
        return workflowInstances.get(instanceId);
    }

    /**
     * Cancels a running workflow instance.
     * In a real scenario, this would trigger compensation logic.
     *
     * @param instanceId The ID of the workflow instance to cancel.
     */
    public void cancelWorkflow(UUID instanceId) {
        WorkflowInstance instance = workflowInstances.get(instanceId);
        if (instance == null) {
            log.warn("Attempted to cancel non-existent workflow instance: {}", instanceId);
            return;
        }
        if (instance.getStatus().equals(WorkflowStatus.COMPLETED) || instance.getStatus().equals(WorkflowStatus.FAILED) || instance.getStatus().equals(WorkflowStatus.CANCELED)) {
            log.info("Workflow instance {} is already in a terminal state ({}). Cannot cancel.", instanceId, instance.getStatus());
            return;
        }
        updateWorkflowStatus(instanceId, WorkflowStatus.CANCELED, "Cancelled by user request.");
        log.info("Workflow instance {} has been cancelled.", instanceId);
        // TODO: Implement compensation logic here. Iterate through completed steps in reverse
        // and trigger their compensation actions (e.g., via Kafka messages or direct REST calls).
    }
}
