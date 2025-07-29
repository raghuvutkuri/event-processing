package com.events.payments.config;

import com.events.payments.model.WorkflowDefinition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "orchestrator")
@Data
public class WorkflowConfig {
    // Maps workflow names to their definitions
    private Map<String, WorkflowDefinition> workflows;
}
