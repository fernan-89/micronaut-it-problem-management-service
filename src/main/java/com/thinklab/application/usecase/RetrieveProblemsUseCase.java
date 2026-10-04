package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.application.mapper.ProblemMapper;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's Problems, filterable (BIAN Behavior Qualifier: {@code retrieve}, collection). Staff only. */
@Singleton
public class RetrieveProblemsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveProblemsUseCase.class);

    private final ProblemRepository problemRepository;

    public RetrieveProblemsUseCase(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public Flux<ProblemResponse> execute(UUID organisationId, ProblemStatus status, Priority priority, UUID assigneeId, UUID incidentId, UUID assetId,
                                         boolean openOnly, String role) {
        log.info("[USE CASE] Retrieving Problems for organisation: {} status: {} priority: {}", organisationId, status, priority);

        return Mono.fromRunnable(() -> ProblemWorkflow.requireStaff(role, "list problems"))
                .thenMany(Flux.defer(() -> problemRepository.findAll(organisationId,
                        new ProblemRepository.Filter(status, priority, assigneeId, incidentId, assetId, openOnly))))
                .map(ProblemMapper::toResponse);
    }
}
