package dev.mark.eval.service;

import dev.mark.agent.model.AgentStatusModel;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.eval.model.EvalResultModel;
import dev.mark.eval.model.EvalScenarioModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class EvalRunnerService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunnerService.class);
    private final AgentOrchestratorService orchestratorService;

    public EvalRunnerService(AgentOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    public List<EvalResultModel> runEvaluations(List<EvalScenarioModel> scenarios) {
        List<EvalResultModel> results = new ArrayList<>();
        int passedCount = 0;

        log.info("=== STARTING EVALUATION RUN ({} scenarios) ===", scenarios.size());

        for (EvalScenarioModel scenario : scenarios) {
            log.info("--- EVAL SCENARIO: {} ---", scenario.id());
            String taskId = UUID.randomUUID().toString();
            
            try {
                TaskResponseDTO response = orchestratorService.executeTask(taskId, scenario.goal());

                boolean passed = true;
                String failureReason = "";

                if (response.status() != AgentStatusModel.COMPLETED) {
                    passed = false;
                    failureReason = "Agent status was " + response.status() + " instead of COMPLETED.";
                } else if (scenario.expectedFinalAnswerRegex() != null && !scenario.expectedFinalAnswerRegex().isBlank()) {
                    String answer = response.finalAnswer() != null ? response.finalAnswer() : "";
                    if (!Pattern.compile(scenario.expectedFinalAnswerRegex()).matcher(answer).find()) {
                        passed = false;
                        failureReason = "Final answer did not match expected regex: " + scenario.expectedFinalAnswerRegex();
                    }
                }

                if (passed) {
                    log.info("--- EVAL SCENARIO PASSED: {} ---", scenario.id());
                    passedCount++;
                } else {
                    log.warn("--- EVAL SCENARIO FAILED: {} Reason: {} ---", scenario.id(), failureReason);
                }

                results.add(new EvalResultModel(
                        scenario.id(),
                        passed,
                        response.status(),
                        response.finalAnswer(),
                        failureReason
                ));

            } catch (Exception e) {
                log.error("--- EVAL SCENARIO EXCEPTION: {} ---", scenario.id(), e);
                results.add(new EvalResultModel(
                        scenario.id(),
                        false,
                        AgentStatusModel.FAILED,
                        null,
                        "Exception during execution: " + e.getMessage()
                ));
            }
        }

        log.info("=== EVALUATION RUN COMPLETE ({}/{} passed) ===", passedCount, scenarios.size());
        return results;
    }
}
