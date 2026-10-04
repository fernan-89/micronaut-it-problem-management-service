package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssignProblemRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiateProblemRequest;
import com.thinklab.application.dto.request.RecordAnalysisRequest;
import com.thinklab.application.dto.request.ReopenProblemRequest;
import com.thinklab.application.dto.request.ResolveProblemRequest;
import com.thinklab.application.dto.request.UpdateProblemRequest;
import com.thinklab.domain.exception.InvalidProblemStatusException;
import com.thinklab.domain.exception.ProblemAccessDeniedException;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ProblemRepository;
import com.thinklab.domain.repository.ProblemRepository.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemUseCaseTest {

    private static final String REQUESTER = "REQUESTER";

    @Mock private ProblemRepository repository;
    @Mock private HashServicePort hashService;

    private final UUID org = UUID.randomUUID();
    private ProblemWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ProblemWorkflow(repository);
    }

    private Problem problem() {
        return Problem.createNew(UUID.randomUUID(), org, "Switch drops packets", "Intermittent", Priority.P2, Set.of(), Set.of(), Set.of(), "op-1");
    }

    private Problem found(Problem problem) {
        when(repository.findById(problem.getId(), org)).thenReturn(Mono.just(problem));
        return problem;
    }

    private UUID missing() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown, org)).thenReturn(Mono.empty());
        return unknown;
    }

    @Test
    @DisplayName("initiate opens a problem under a sovereign id; a REQUESTER is refused before anything is spent")
    void initiate() {
        UUID id = UUID.randomUUID();
        when(hashService.generateSovereignId("problem-creation")).thenReturn(Mono.just(id));
        when(repository.create(any(Problem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var request = new InitiateProblemRequest("Switch drops packets", "Intermittent", Priority.P2, Set.of(UUID.randomUUID()), null, null);
        InitiateProblemUseCase useCase = new InitiateProblemUseCase(hashService, repository);

        StepVerifier.create(useCase.execute(org, request, "op-1", null)).assertNext(created -> {
            assertEquals(id, created.id());
            assertEquals("NEW", created.status());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, request, "op-1", REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("retrieve reads inside the tenant, is for staff, and an unknown id is a 404")
    void retrieve() {
        Problem problem = found(problem());
        UUID unknownId = missing();
        RetrieveProblemUseCase useCase = new RetrieveProblemUseCase(repository);

        StepVerifier.create(useCase.execute(problem.getId(), org, "ADMIN")).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(problem.getId(), org, REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, null)).expectError(ProblemNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieve all passes every filter to the repository and is for staff")
    void retrieveAll() {
        when(repository.findAll(eq(org), any(Filter.class))).thenReturn(Flux.just(problem(), problem()));
        UUID anyId = UUID.randomUUID();
        RetrieveProblemsUseCase useCase = new RetrieveProblemsUseCase(repository);

        StepVerifier.create(useCase.execute(org, ProblemStatus.NEW, Priority.P2, anyId, anyId, anyId, true, null)).expectNextCount(2).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, null, null, false, REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();

        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(repository).findAll(eq(org), filter.capture());
        assertEquals(new Filter(ProblemStatus.NEW, Priority.P2, anyId, anyId, anyId, true), filter.getValue());
    }

    @Test
    @DisplayName("update, assign and analysis go through a guarded save with the status the problem was loaded in")
    void editing() {
        Problem problem = found(problem());
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        UUID assignee = UUID.randomUUID();

        StepVerifier.create(new UpdateProblemUseCase(workflow).execute(problem.getId(), org,
                new UpdateProblemRequest("New title", "New description", Priority.P1, Set.of(), Set.of(), Set.of()), "op-1", null)).verifyComplete();
        assertEquals(Priority.P1, problem.getPriority());
        StepVerifier.create(new AssignProblemUseCase(workflow).execute(problem.getId(), org, new AssignProblemRequest(assignee), "op-1", null)).verifyComplete();
        assertEquals(assignee, problem.getAssigneeId());
        verify(repository, times(2)).save(eq(problem), eq(ProblemStatus.NEW), any());

        problem.investigate("op-1");
        StepVerifier.create(new RecordAnalysisUseCase(workflow).execute(problem.getId(), org, new RecordAnalysisRequest("Bad firmware", null), "op-1", null)).verifyComplete();
        assertEquals("Bad firmware", problem.getRootCause());
        verify(repository).save(eq(problem), eq(ProblemStatus.UNDER_INVESTIGATION), any());
    }

    @Test
    @DisplayName("the lifecycle moves: investigate, known error, resolve, reopen, close and cancel")
    void lifecycle() {
        Problem problem = found(problem());
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        ControlProblemUseCase control = new ControlProblemUseCase(workflow);
        UUID id = problem.getId();

        StepVerifier.create(control.execute(id, org, ControlProblemUseCase.Action.INVESTIGATE, "op-1", null)).verifyComplete();
        problem.recordAnalysis("Bad firmware", "Reboot", "op-1");
        StepVerifier.create(control.execute(id, org, ControlProblemUseCase.Action.KNOWN_ERROR, "op-1", null)).verifyComplete();
        StepVerifier.create(new ResolveProblemUseCase(workflow).execute(id, org, new ResolveProblemRequest("Upgraded"), "op-1", null)).verifyComplete();
        StepVerifier.create(new ReopenProblemUseCase(workflow).execute(id, org, new ReopenProblemRequest("Not fixed"), "op-1", null)).verifyComplete();
        assertEquals(ProblemStatus.UNDER_INVESTIGATION, problem.getStatus());
        StepVerifier.create(new ResolveProblemUseCase(workflow).execute(id, org, new ResolveProblemRequest("Upgraded again"), "op-1", null)).verifyComplete();
        StepVerifier.create(control.execute(id, org, ControlProblemUseCase.Action.CLOSE, "op-1", null)).verifyComplete();
        assertEquals(ProblemStatus.CLOSED, problem.getStatus());

        Problem other = found(problem());
        StepVerifier.create(control.execute(other.getId(), org, ControlProblemUseCase.Action.CANCEL, "op-1", null)).verifyComplete();
        assertEquals(ProblemStatus.CANCELLED, other.getStatus());
    }

    @Test
    @DisplayName("staff actions refuse a REQUESTER, an unknown problem and an illegal transition, and save nothing")
    void refusals() {
        Problem problem = found(problem());
        UUID unknownId = missing();
        ControlProblemUseCase control = new ControlProblemUseCase(workflow);

        StepVerifier.create(control.execute(problem.getId(), org, ControlProblemUseCase.Action.INVESTIGATE, "op-1", REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();
        StepVerifier.create(control.execute(unknownId, org, ControlProblemUseCase.Action.INVESTIGATE, "op-1", null)).expectError(ProblemNotFoundException.class).verify();
        StepVerifier.create(control.execute(problem.getId(), org, ControlProblemUseCase.Action.CLOSE, "op-1", null)).expectError(InvalidProblemStatusException.class).verify();
        verify(repository, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("a comment is added with a sovereign id; a REQUESTER and an unknown problem are refused")
    void comment() {
        Problem problem = found(problem());
        UUID unknownId = missing();
        when(hashService.generateSovereignId("problem-comment-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.addComment(any(), any(), any(), any())).thenReturn(Mono.empty());
        InitiateCommentUseCase useCase = new InitiateCommentUseCase(hashService, repository);
        var body = new InitiateCommentRequest("Checked the logs");

        StepVerifier.create(useCase.execute(problem.getId(), org, body, "op-1", null)).verifyComplete();
        verify(repository).addComment(eq(problem.getId()), eq(org), any(), any());
        StepVerifier.create(useCase.execute(problem.getId(), org, body, "op-1", REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, body, "op-1", null)).expectError(ProblemNotFoundException.class).verify();
    }

    @Test
    @DisplayName("the audit log is for staff; an unknown problem is a 404")
    void auditLog() {
        Problem problem = found(problem());
        UUID unknownId = missing();
        RetrieveProblemAuditLogUseCase useCase = new RetrieveProblemAuditLogUseCase(repository);

        StepVerifier.create(useCase.execute(problem.getId(), org, null)).assertNext(log -> assertEquals(1, log.size())).verifyComplete();
        StepVerifier.create(useCase.execute(problem.getId(), org, REQUESTER)).expectError(ProblemAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, "ADMIN")).expectError(ProblemNotFoundException.class).verify();
    }
}
