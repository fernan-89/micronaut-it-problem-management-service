package com.thinklab.application.usecase;

import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for the lifecycle moves that need no payload (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlProblemUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        INVESTIGATE {
            @Override ProblemAuditEntry apply(Problem problem, String executor) { return problem.investigate(executor); }
        },
        KNOWN_ERROR {
            @Override ProblemAuditEntry apply(Problem problem, String executor) { return problem.markKnownError(executor); }
        },
        CLOSE {
            @Override ProblemAuditEntry apply(Problem problem, String executor) { return problem.close(executor); }
        },
        CANCEL {
            @Override ProblemAuditEntry apply(Problem problem, String executor) { return problem.cancel(executor); }
        };

        abstract ProblemAuditEntry apply(Problem problem, String executor);
    }

    private final ProblemWorkflow workflow;

    public ControlProblemUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on Problem ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "work a problem", problem -> action.apply(problem, executor));
    }
}
