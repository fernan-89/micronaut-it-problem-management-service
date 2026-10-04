package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateProblemRequest;
import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.application.mapper.ProblemMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for opening a Problem (BIAN Behavior Qualifier: {@code initiate}). Staff only (ADR-032); the id is a sovereign UUID. */
@Singleton
public class InitiateProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateProblemUseCase.class);

    private final HashServicePort hashServicePort;
    private final ProblemRepository problemRepository;

    public InitiateProblemUseCase(HashServicePort hashServicePort, ProblemRepository problemRepository) {
        this.hashServicePort = hashServicePort;
        this.problemRepository = problemRepository;
    }

    public Mono<ProblemResponse> execute(UUID organisationId, InitiateProblemRequest request, String executor, String role) {
        log.info("[USE CASE] Opening a Problem for organisation: {}", organisationId);

        return Mono.fromRunnable(() -> ProblemWorkflow.requireStaff(role, "open a problem"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("problem-creation")))
                .map(sovereignId -> ProblemMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(problemRepository::create)
                .map(ProblemMapper::toResponse);
    }
}
