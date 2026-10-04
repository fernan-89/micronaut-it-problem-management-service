package com.thinklab.domain.repository;

import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;
import com.thinklab.domain.model.Problem.ProblemStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for Problem persistence (IT Problem Management Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002). {@link #create} is the only whole-document write; {@link #save} persists the
 * state a domain operation reached together with its audit entry in one atomic update, and only while the problem is still in the status
 * it had when it was loaded (ADR-033). There is no {@code deleteById}. Every lookup is tenant-scoped: another organisation's problem is
 * simply not found.
 */
public interface ProblemRepository {

    Mono<Problem> create(Problem problem);

    Mono<Problem> findById(UUID id, UUID organisationId);

    Flux<Problem> findAll(UUID organisationId, Filter filter);

    /** Persists the state the problem reached with its audit entry; a lost race is {@link com.thinklab.domain.exception.InvalidProblemStatusException} (409, retry). */
    Mono<Void> save(Problem problem, ProblemStatus expectedStatus, ProblemAuditEntry auditEntry);

    Mono<Void> addComment(UUID id, UUID organisationId, Comment comment, ProblemAuditEntry auditEntry);

    /**
     * Optional filters of the collection. {@code incidentId} answers "which problems explain this incident" and {@code assetId} "what is
     * known about this asset"; {@code openOnly} leaves out RESOLVED, CLOSED and CANCELLED.
     */
    record Filter(ProblemStatus status, Priority priority, UUID assigneeId, UUID incidentId, UUID assetId, boolean openOnly) {
    }
}
