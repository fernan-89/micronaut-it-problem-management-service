package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ProblemAccessDeniedException;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/**
 * The shared shape of every staff action on a problem: refuse a REQUESTER (ADR-032), load the problem inside the tenant, apply the domain
 * behavior and save the state it reached with its audit entry as a guarded write (ADR-033), so a lost race is a 409 and never a lost update.
 */
@Singleton
public class ProblemWorkflow {

    static final String REQUESTER_ROLE = "REQUESTER";

    private final ProblemRepository problemRepository;

    public ProblemWorkflow(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    /** Problems are the work of IT staff: a REQUESTER is refused whatever they ask for. */
    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new ProblemAccessDeniedException(operation);
        }
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<Problem, ProblemAuditEntry> action) {
        return Mono.fromRunnable(() -> requireStaff(role, operation))
                .then(Mono.defer(() -> problemRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ProblemNotFoundException(id)))
                .flatMap(problem -> {
                    var statusBefore = problem.getStatus();
                    ProblemAuditEntry entry = action.apply(problem);
                    return problemRepository.save(problem, statusBefore, entry);
                });
    }
}
