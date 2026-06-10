package com.cooperativesolutionism.nmsci.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (NmsciIntegrationTestBase.isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }

        return ConditionEvaluationResult.disabled(NmsciIntegrationTestBase.disabledReason());
    }
}
