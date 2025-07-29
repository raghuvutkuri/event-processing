package com.events.payments.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Represents a workflow definition loaded from YAML
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {
    private String name;
    private String description;
    private List<StepDefinition> steps;
}