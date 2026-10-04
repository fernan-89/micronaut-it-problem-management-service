package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ProblemRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Use Case for adding a Comment to a Problem (BIAN Behavior Qualifier: {@code comment/initiate}). Staff only, like everything here. */
@Singleton
public class InitiateCommentUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateCommentUseCase.class);

    private final HashServicePort hashServicePort;
    private final ProblemRepository problemRepository;

    public InitiateCommentUseCase(HashServicePort hashServicePort, ProblemRepository problemRepository) {
        this.hashServicePort = hashServicePort;
        this.problemRepository = problemRepository;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, InitiateCommentRequest request, String executor, String role) {
        log.info("[USE CASE] Adding a comment to Problem ID: {}", id);

        return Mono.fromRunnable(() -> ProblemWorkflow.requireStaff(role, "comment on a problem"))
                .then(Mono.defer(() -> problemRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ProblemNotFoundException(id)))
                .flatMap(problem -> hashServicePort.generateSovereignId("problem-comment-creation")
                        .flatMap(commentId -> {
                            Comment comment = new Comment(commentId, executor, request.text(), Instant.now());
                            var entry = problem.addComment(comment, executor);
                            return problemRepository.addComment(id, organisationId, comment, entry);
                        }));
    }
}
