package com.events.payments.controller;

import com.events.payments.model.WorkflowInstance;
import com.events.payments.service.WorkflowOrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@Slf4j
public class WorkflowController {

    private final WorkflowOrchestratorService workflowOrchestratorService;

    public WorkflowController(WorkflowOrchestratorService workflowOrchestratorService) {
        this.workflowOrchestratorService = workflowOrchestratorService;
    }

    /**
     * REST endpoint to start a new workflow instance.
     * Example: POST /api/workflows/start/onboarding-flow
     * Body: { "username": "testuser", "email": "test@example.com" }
     *
     * @param workflowName   The name of the workflow to start.
     * @param initialContext Initial data for the workflow's transaction context.
     * @return The ID of the started workflow instance.
     */
    @PostMapping("/start/{workflowName}")
    public ResponseEntity<String> startWorkflow(
            @PathVariable String workflowName,
            @RequestBody Map<String, Object> initialContext) {
        try {
            UUID instanceId = workflowOrchestratorService.startWorkflow(workflowName, initialContext);
            log.info("Workflow '{}' started with instance ID: {}", workflowName, instanceId);
            return ResponseEntity.ok("Workflow started. Instance ID: " + instanceId);
        } catch (IllegalArgumentException e) {
            log.error("Failed to start workflow: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred while starting workflow '{}': {}", workflowName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * REST endpoint to get the status of a workflow instance.
     * Example: GET /api/workflows/status/{instanceId}
     *
     * @param instanceId The ID of the workflow instance.
     * @return The detailed status of the workflow instance.
     */
    @GetMapping("/status/{instanceId}")
    public ResponseEntity<WorkflowInstance> getWorkflowStatus(@PathVariable UUID instanceId) {
        WorkflowInstance instance = workflowOrchestratorService.getWorkflowStatus(instanceId);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(instance);
    }

    /**
     * REST endpoint to cancel a workflow instance.
     * Example: POST /api/workflows/cancel/{instanceId}
     *
     * @param instanceId The ID of the workflow instance to cancel.
     * @return A confirmation message.
     */
    @PostMapping("/cancel/{instanceId}")
    public ResponseEntity<String> cancelWorkflow(@PathVariable UUID instanceId) {
        try {
            workflowOrchestratorService.cancelWorkflow(instanceId);
            return ResponseEntity.ok("Workflow instance " + instanceId + " cancellation initiated.");
        } catch (Exception e) {
            log.error("Error cancelling workflow instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error cancelling workflow: " + e.getMessage());
        }
    }
}
