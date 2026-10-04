package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.application.mapper.ProblemMapper;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one Problem of the tenant (BIAN Behavior Qualifier: {@code retrieve}). Staff only. */
@Singleton
public class RetrieveProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveProblemUseCase.class);

    private final ProblemRepository problemRepository;

    public RetrieveProblemUseCase(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public Mono<ProblemResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving Problem by ID: {}", id);

        return Mono.fromRunnable(() -> ProblemWorkflow.requireStaff(role, "read a problem"))
                .then(Mono.defer(() -> problemRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ProblemNotFoundException(id)))
                .map(ProblemMapper::toResponse);
    }
}
