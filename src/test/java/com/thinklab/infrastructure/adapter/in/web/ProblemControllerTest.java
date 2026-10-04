package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.AssignProblemRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiateProblemRequest;
import com.thinklab.application.dto.request.RecordAnalysisRequest;
import com.thinklab.application.dto.request.ReopenProblemRequest;
import com.thinklab.application.dto.request.ResolveProblemRequest;
import com.thinklab.application.dto.request.UpdateProblemRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.application.usecase.AssignProblemUseCase;
import com.thinklab.application.usecase.ControlProblemUseCase;
import com.thinklab.application.usecase.InitiateCommentUseCase;
import com.thinklab.application.usecase.InitiateProblemUseCase;
import com.thinklab.application.usecase.RecordAnalysisUseCase;
import com.thinklab.application.usecase.ReopenProblemUseCase;
import com.thinklab.application.usecase.ResolveProblemUseCase;
import com.thinklab.application.usecase.RetrieveProblemAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveProblemUseCase;
import com.thinklab.application.usecase.RetrieveProblemsUseCase;
import com.thinklab.application.usecase.UpdateProblemUseCase;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class ProblemControllerTest {

    private static final String EXECUTOR = "op-1";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();

    @Mock private InitiateProblemUseCase initiateProblemUseCase;
    @Mock private RetrieveProblemUseCase retrieveProblemUseCase;
    @Mock private RetrieveProblemsUseCase retrieveProblemsUseCase;
    @Mock private UpdateProblemUseCase updateProblemUseCase;
    @Mock private AssignProblemUseCase assignProblemUseCase;
    @Mock private RecordAnalysisUseCase recordAnalysisUseCase;
    @Mock private ControlProblemUseCase controlProblemUseCase;
    @Mock private ResolveProblemUseCase resolveProblemUseCase;
    @Mock private ReopenProblemUseCase reopenProblemUseCase;
    @Mock private InitiateCommentUseCase initiateCommentUseCase;
    @Mock private RetrieveProblemAuditLogUseCase retrieveProblemAuditLogUseCase;

    private ProblemController controller;

    @BeforeEach
    void setUp() {
        controller = new ProblemController(initiateProblemUseCase, retrieveProblemUseCase, retrieveProblemsUseCase, updateProblemUseCase,
                assignProblemUseCase, recordAnalysisUseCase, controlProblemUseCase, resolveProblemUseCase, reopenProblemUseCase,
                initiateCommentUseCase, retrieveProblemAuditLogUseCase);
    }

    private ProblemResponse sample() {
        return new ProblemResponse(id, tenant, "t", "d", "P2", "NEW", null, Set.of(), Set.of(), Set.of(), null, null, null, 0, List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate answers 201 with the new problem")
    void initiate() {
        var body = new InitiateProblemRequest("t", "d", Priority.P2, null, null, null);
        when(initiateProblemUseCase.execute(tenant, body, EXECUTOR, "ADMIN")).thenReturn(Mono.just(sample()));

        StepVerifier.create(controller.initiate(tenantHeader, EXECUTOR, "ADMIN", body)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve (one and all) carries tenant and role to the use case")
    void retrieve() {
        UUID anyId = UUID.randomUUID();
        when(retrieveProblemUseCase.execute(id, tenant, "ADMIN")).thenReturn(Mono.just(sample()));
        when(retrieveProblemsUseCase.execute(tenant, ProblemStatus.NEW, Priority.P1, anyId, anyId, anyId, true, null)).thenReturn(Flux.just(sample(), sample()));

        StepVerifier.create(controller.retrieveById(id, tenantHeader, "ADMIN")).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveAll(tenantHeader, null, ProblemStatus.NEW, Priority.P1, anyId, anyId, anyId, true)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, assignment, analysis, resolve and reopen answer 204")
    void commands() {
        var update = new UpdateProblemRequest("t", "d", Priority.P1, null, null, null);
        var assign = new AssignProblemRequest(UUID.randomUUID());
        var analysis = new RecordAnalysisRequest("cause", null);
        var resolve = new ResolveProblemRequest("fixed");
        var reopen = new ReopenProblemRequest("not fixed");
        when(updateProblemUseCase.execute(id, tenant, update, EXECUTOR, null)).thenReturn(Mono.empty());
        when(assignProblemUseCase.execute(id, tenant, assign, EXECUTOR, null)).thenReturn(Mono.empty());
        when(recordAnalysisUseCase.execute(id, tenant, analysis, EXECUTOR, null)).thenReturn(Mono.empty());
        when(resolveProblemUseCase.execute(id, tenant, resolve, EXECUTOR, null)).thenReturn(Mono.empty());
        when(reopenProblemUseCase.execute(id, tenant, reopen, EXECUTOR, null)).thenReturn(Mono.empty());

        StepVerifier.create(controller.update(id, tenantHeader, EXECUTOR, null, update)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.assign(id, tenantHeader, EXECUTOR, null, assign)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.recordAnalysis(id, tenantHeader, EXECUTOR, null, analysis)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlResolve(id, tenantHeader, EXECUTOR, null, resolve)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlReopen(id, tenantHeader, EXECUTOR, null, reopen)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("investigate, known-error, close and cancel answer 204 with the right action")
    void controls() {
        when(controlProblemUseCase.execute(eq(id), eq(tenant), any(ControlProblemUseCase.Action.class), eq(EXECUTOR), eq(null))).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlInvestigate(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlKnownError(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlClose(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlCancel(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        verify(controlProblemUseCase).execute(id, tenant, ControlProblemUseCase.Action.INVESTIGATE, EXECUTOR, null);
        verify(controlProblemUseCase).execute(id, tenant, ControlProblemUseCase.Action.KNOWN_ERROR, EXECUTOR, null);
        verify(controlProblemUseCase).execute(id, tenant, ControlProblemUseCase.Action.CLOSE, EXECUTOR, null);
        verify(controlProblemUseCase).execute(id, tenant, ControlProblemUseCase.Action.CANCEL, EXECUTOR, null);
    }

    @Test
    @DisplayName("a comment answers 201 and the audit log is returned as one list")
    void commentAndAudit() {
        var body = new InitiateCommentRequest("Checked the logs");
        when(initiateCommentUseCase.execute(id, tenant, body, EXECUTOR, "ADMIN")).thenReturn(Mono.empty());
        when(retrieveProblemAuditLogUseCase.execute(id, tenant, "ADMIN")).thenReturn(Mono.just(List.of(new AuditEntryResponse(Instant.now(), "INITIATED", "op", null, "NEW", "d"))));

        StepVerifier.create(controller.initiateComment(id, tenantHeader, EXECUTOR, "ADMIN", body)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveAuditLog(id, tenantHeader, "ADMIN")).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }
}
