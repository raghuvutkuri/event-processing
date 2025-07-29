package com.events.payments.model;

// Enum for workflow and step statuses
public enum WorkflowStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELED,
    COMPENSATING,
    PENDING, // For steps
    RETRIED // For steps
}