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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-problem-management} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Problem} is the Control Record. Every route follows
 * {@code /it-problem-management/v1/{control-record-id}/{behavior-qualifier}}. There is no {@code DELETE}: {@code control/cancel} and
 * {@code control/close} are terminal, soft status transitions.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} is mandatory everywhere and scopes every lookup (another
 * tenant's problem is a 404). {@code X-Role}, set by the kit's {@code SecurityFilter} from the verified token when security is on, makes
 * every route refuse a {@code REQUESTER} with 403 {@code ERR-PRB-00403}.
 */
@Controller("/it-problem-management/v1")
public class ProblemController {

    private static final Logger log = LoggerFactory.getLogger(ProblemController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateProblemUseCase initiateProblemUseCase;
    private final RetrieveProblemUseCase retrieveProblemUseCase;
    private final RetrieveProblemsUseCase retrieveProblemsUseCase;
    private final UpdateProblemUseCase updateProblemUseCase;
    private final AssignProblemUseCase assignProblemUseCase;
    private final RecordAnalysisUseCase recordAnalysisUseCase;
    private final ControlProblemUseCase controlProblemUseCase;
    private final ResolveProblemUseCase resolveProblemUseCase;
    private final ReopenProblemUseCase reopenProblemUseCase;
    private final InitiateCommentUseCase initiateCommentUseCase;
    private final RetrieveProblemAuditLogUseCase retrieveProblemAuditLogUseCase;

    public ProblemController(
            InitiateProblemUseCase initiateProblemUseCase,
            RetrieveProblemUseCase retrieveProblemUseCase,
            RetrieveProblemsUseCase retrieveProblemsUseCase,
            UpdateProblemUseCase updateProblemUseCase,
            AssignProblemUseCase assignProblemUseCase,
            RecordAnalysisUseCase recordAnalysisUseCase,
            ControlProblemUseCase controlProblemUseCase,
            ResolveProblemUseCase resolveProblemUseCase,
            ReopenProblemUseCase reopenProblemUseCase,
            InitiateCommentUseCase initiateCommentUseCase,
            RetrieveProblemAuditLogUseCase retrieveProblemAuditLogUseCase
    ) {
        this.initiateProblemUseCase = initiateProblemUseCase;
        this.retrieveProblemUseCase = retrieveProblemUseCase;
        this.retrieveProblemsUseCase = retrieveProblemsUseCase;
        this.updateProblemUseCase = updateProblemUseCase;
        this.assignProblemUseCase = assignProblemUseCase;
        this.recordAnalysisUseCase = recordAnalysisUseCase;
        this.controlProblemUseCase = controlProblemUseCase;
        this.resolveProblemUseCase = resolveProblemUseCase;
        this.reopenProblemUseCase = reopenProblemUseCase;
        this.initiateCommentUseCase = initiateCommentUseCase;
        this.retrieveProblemAuditLogUseCase = retrieveProblemAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Opens a new Problem. */
    @Post("/initiate")
    public Mono<HttpResponse<ProblemResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateProblemRequest request
    ) {
        log.info("[ACTION: INITIATE_PROBLEM] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiateProblemUseCase.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single Problem of the tenant. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<ProblemResponse>> retrieveById(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role
    ) {
        log.info("[ACTION: RETRIEVE_PROBLEM] Received request to get Problem by ID: {}", id);

        return Mono.defer(() -> retrieveProblemUseCase.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Filterable; {@code incidentId} lists the problems that explain an incident. */
    @Get("/retrieve")
    public Mono<List<ProblemResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(ROLE_HEADER) @Nullable String role,
            @QueryValue @Nullable ProblemStatus status,
            @QueryValue @Nullable Priority priority,
            @QueryValue @Nullable UUID assigneeId,
            @QueryValue @Nullable UUID incidentId,
            @QueryValue @Nullable UUID assetId,
            @QueryValue(defaultValue = "false") boolean openOnly
    ) {
        log.info("[ACTION: RETRIEVE_PROBLEMS] Received request to list Problems for organisation: {} status: {} priority: {}", tenantId, status, priority);

        return Mono.defer(() -> retrieveProblemsUseCase
                .execute(UUID.fromString(tenantId), status, priority, assigneeId, incidentId, assetId, openOnly, role).collectList());
    }

    /** Behavior Qualifier: {@code update}. Title, description, priority and links, while the problem is open. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateProblemRequest request
    ) {
        return Mono.defer(() -> updateProblemUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code assignment/update}. Names who investigates. */
    @Put("/{id}/assignment/update")
    public Mono<HttpResponse<Void>> assign(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid AssignProblemRequest request
    ) {
        return Mono.defer(() -> assignProblemUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code analysis/update}. Records the root cause and/or the workaround. */
    @Put("/{id}/analysis/update")
    public Mono<HttpResponse<Void>> recordAnalysis(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid RecordAnalysisRequest request
    ) {
        return Mono.defer(() -> recordAnalysisUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/investigate}. NEW -&gt; UNDER_INVESTIGATION. */
    @Put("/{id}/control/investigate")
    public Mono<HttpResponse<Void>> controlInvestigate(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                       @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlProblemUseCase.Action.INVESTIGATE, executor, role);
    }

    /** Behavior Qualifier: {@code control/known-error}. UNDER_INVESTIGATION -&gt; KNOWN_ERROR; needs a root cause and a workaround on record. */
    @Put("/{id}/control/known-error")
    public Mono<HttpResponse<Void>> controlKnownError(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                      @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlProblemUseCase.Action.KNOWN_ERROR, executor, role);
    }

    /** Behavior Qualifier: {@code control/resolve}. UNDER_INVESTIGATION or KNOWN_ERROR -&gt; RESOLVED; needs the root cause and the resolution. */
    @Put("/{id}/control/resolve")
    public Mono<HttpResponse<Void>> controlResolve(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ResolveProblemRequest request
    ) {
        return Mono.defer(() -> resolveProblemUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    @Put("/{id}/control/close")
    public Mono<HttpResponse<Void>> controlClose(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                 @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlProblemUseCase.Action.CLOSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; UNDER_INVESTIGATION; needs a reason. */
    @Put("/{id}/control/reopen")
    public Mono<HttpResponse<Void>> controlReopen(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReopenProblemRequest request
    ) {
        return Mono.defer(() -> reopenProblemUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/cancel}. Terminal, replaces DELETE. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                  @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlProblemUseCase.Action.CANCEL, executor, role);
    }

    /** Behavior Qualifier: {@code comment/initiate}. */
    @Post("/{id}/comment/initiate")
    public Mono<HttpResponse<Void>> initiateComment(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateCommentRequest request
    ) {
        return Mono.defer(() -> initiateCommentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role))
                .thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the Problem. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                           @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveProblemAuditLogUseCase.execute(id, UUID.fromString(tenantId), role));
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlProblemUseCase.Action action, String executor, String role) {
        log.info("[ACTION: CONTROL_PROBLEM] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return Mono.defer(() -> controlProblemUseCase.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
