package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.ProblemMapper;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a Problem (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveProblemAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveProblemAuditLogUseCase.class);

    private final ProblemRepository problemRepository;

    public RetrieveProblemAuditLogUseCase(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of Problem ID: {}", id);

        return Mono.fromRunnable(() -> ProblemWorkflow.requireStaff(role, "read the audit trail of a problem"))
                .then(Mono.defer(() -> problemRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ProblemNotFoundException(id)))
                .map(problem -> problem.getAuditTrail().stream().map(ProblemMapper::toResponse).collect(Collectors.toList()));
    }
}
