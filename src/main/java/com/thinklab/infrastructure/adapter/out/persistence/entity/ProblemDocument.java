package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;
import com.thinklab.domain.model.Problem.ProblemStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the Problem Aggregate for MongoDB. */
@Introspected
public class ProblemDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private UUID assigneeId;
    private List<UUID> relatedIncidentIds = new ArrayList<>();
    private List<UUID> relatedChangeIds = new ArrayList<>();
    private List<UUID> affectedAssetIds = new ArrayList<>();
    private String rootCause;
    private String workaround;
    private String resolution;
    private int reopenCount;
    private List<CommentDocument> comments = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getAssigneeId() { return assigneeId; }
    public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }
    public List<UUID> getRelatedIncidentIds() { return relatedIncidentIds; }
    public void setRelatedIncidentIds(List<UUID> relatedIncidentIds) { this.relatedIncidentIds = relatedIncidentIds; }
    public List<UUID> getRelatedChangeIds() { return relatedChangeIds; }
    public void setRelatedChangeIds(List<UUID> relatedChangeIds) { this.relatedChangeIds = relatedChangeIds; }
    public List<UUID> getAffectedAssetIds() { return affectedAssetIds; }
    public void setAffectedAssetIds(List<UUID> affectedAssetIds) { this.affectedAssetIds = affectedAssetIds; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getWorkaround() { return workaround; }
    public void setWorkaround(String workaround) { this.workaround = workaround; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public int getReopenCount() { return reopenCount; }
    public void setReopenCount(int reopenCount) { this.reopenCount = reopenCount; }
    public List<CommentDocument> getComments() { return comments; }
    public void setComments(List<CommentDocument> comments) { this.comments = comments; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record CommentDocument(UUID commentId, String author, String text, Instant createdAt) {
        public static CommentDocument fromDomain(Comment comment) {
            return new CommentDocument(comment.commentId(), comment.author(), comment.text(), comment.createdAt());
        }

        Comment toDomain() { return new Comment(commentId, author, text, createdAt); }
    }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(ProblemAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        ProblemAuditEntry toDomain() {
            return new ProblemAuditEntry(occurredAt, action, executor, fromStatus != null ? ProblemStatus.valueOf(fromStatus) : null,
                    ProblemStatus.valueOf(toStatus), detail);
        }
    }

    public static final class ProblemPersistenceMapper {

        private ProblemPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static ProblemDocument toDocument(Problem problem) {
            ProblemDocument doc = new ProblemDocument();
            doc.setId(problem.getId());
            doc.setOrganisationId(problem.getOrganisationId());
            doc.setTitle(problem.getTitle());
            doc.setDescription(problem.getDescription());
            doc.setPriority(problem.getPriority().name());
            doc.setStatus(problem.getStatus().name());
            doc.setAssigneeId(problem.getAssigneeId());
            doc.setRelatedIncidentIds(new ArrayList<>(problem.getRelatedIncidentIds()));
            doc.setRelatedChangeIds(new ArrayList<>(problem.getRelatedChangeIds()));
            doc.setAffectedAssetIds(new ArrayList<>(problem.getAffectedAssetIds()));
            doc.setRootCause(problem.getRootCause());
            doc.setWorkaround(problem.getWorkaround());
            doc.setResolution(problem.getResolution());
            doc.setReopenCount(problem.getReopenCount());
            doc.setComments(problem.getComments().stream().map(CommentDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            doc.setCreatedAt(problem.getCreatedAt());
            doc.setUpdatedAt(problem.getUpdatedAt());
            doc.setAuditTrail(problem.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Problem toDomain(ProblemDocument doc) {
            return Problem.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getTitle(), doc.getDescription(), Priority.valueOf(doc.getPriority()),
                    ProblemStatus.valueOf(doc.getStatus()), doc.getAssigneeId(), asSet(doc.getRelatedIncidentIds()), asSet(doc.getRelatedChangeIds()),
                    asSet(doc.getAffectedAssetIds()), doc.getRootCause(), doc.getWorkaround(), doc.getResolution(), doc.getReopenCount(),
                    doc.getComments().stream().map(CommentDocument::toDomain).collect(Collectors.toList()), doc.getCreatedAt(), doc.getUpdatedAt(),
                    doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }

        private static Set<UUID> asSet(List<UUID> ids) {
            return new LinkedHashSet<>(ids);
        }
    }
}
