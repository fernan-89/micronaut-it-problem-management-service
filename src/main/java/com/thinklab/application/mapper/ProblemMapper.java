package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateProblemRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.CommentResponse;
import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;

import java.util.UUID;

/** Maps between the Problem aggregate and its DTOs. Static, stateless. */
public final class ProblemMapper {

    private ProblemMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Problem toDomain(InitiateProblemRequest request, UUID sovereignId, UUID organisationId, String executor) {
        return Problem.createNew(sovereignId, organisationId, request.title(), request.description(), request.priority(), request.relatedIncidentIds(),
                request.relatedChangeIds(), request.affectedAssetIds(), executor);
    }

    public static ProblemResponse toResponse(Problem problem) {
        return new ProblemResponse(problem.getId(), problem.getOrganisationId(), problem.getTitle(), problem.getDescription(), problem.getPriority().name(),
                problem.getStatus().name(), problem.getAssigneeId(), problem.getRelatedIncidentIds(), problem.getRelatedChangeIds(),
                problem.getAffectedAssetIds(), problem.getRootCause(), problem.getWorkaround(), problem.getResolution(), problem.getReopenCount(),
                problem.getComments().stream().map(ProblemMapper::toResponse).toList(), problem.getCreatedAt(), problem.getUpdatedAt());
    }

    private static CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.commentId(), comment.author(), comment.text(), comment.createdAt());
    }

    public static AuditEntryResponse toResponse(ProblemAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
