package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidProblemStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the Problem Aggregate Root (BIAN Service Domain: {@code it-problem-management}): the unknown cause behind
 * one or more incidents, worked by IT staff until it is understood and fixed for good (the ITIL "problem", as opposed to the incident,
 * which is only the interruption).
 *
 * <p><b>Lifecycle (ADR-030):</b> {@code NEW -> UNDER_INVESTIGATION -> KNOWN_ERROR -> RESOLVED -> CLOSED}. {@code KNOWN_ERROR} is the state
 * ITIL is built around: the cause is understood and there is a <i>workaround</i>, but no permanent fix yet; it needs both a root cause and
 * a workaround recorded, and it is what a knowledge base serves to the people who hit the incident again. A problem can also be resolved
 * straight from {@code UNDER_INVESTIGATION}; resolving always needs a root cause and the resolution (the permanent fix). A resolved
 * problem can be reopened with a reason (the fix did not hold), and one that is not resolved can be cancelled. There is no {@code DELETE}.
 *
 * <p><b>Priority is chosen by the analyst (ADR-032):</b> unlike an incident, a problem has no urgency to derive it from. Links to the
 * incidents it explains, the changes that fix it and the assets involved are references (ADR-031), never validated against the services
 * that own them.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class Problem {

    public static final int MAX_LINKS = 200;

    private final UUID id;
    private final UUID organisationId;
    private String title;
    private String description;
    private Priority priority;
    private ProblemStatus status;
    private UUID assigneeId;
    private Set<UUID> relatedIncidentIds;
    private Set<UUID> relatedChangeIds;
    private Set<UUID> affectedAssetIds;
    private String rootCause;
    private String workaround;
    private String resolution;
    private int reopenCount;
    private final List<Comment> comments;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ProblemAuditEntry> auditTrail;

    private Problem(UUID id, UUID organisationId, String title, String description, Priority priority, Set<UUID> incidents, Set<UUID> changes,
                    Set<UUID> assets, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = ProblemStatus.NEW;
        this.relatedIncidentIds = new LinkedHashSet<>(incidents);
        this.relatedChangeIds = new LinkedHashSet<>(changes);
        this.affectedAssetIds = new LinkedHashSet<>(assets);
        this.comments = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new ProblemAuditEntry(this.createdAt, "INITIATED", executor, null, ProblemStatus.NEW,
                "Problem opened with priority " + priority + "."));
    }

    private Problem(UUID id, UUID organisationId, String title, String description, Priority priority, ProblemStatus status, UUID assigneeId,
                    Set<UUID> incidents, Set<UUID> changes, Set<UUID> assets, String rootCause, String workaround, String resolution,
                    int reopenCount, List<Comment> comments, Instant createdAt, Instant updatedAt, List<ProblemAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status != null ? status : ProblemStatus.NEW;
        this.assigneeId = assigneeId;
        this.relatedIncidentIds = incidents != null ? new LinkedHashSet<>(incidents) : new LinkedHashSet<>();
        this.relatedChangeIds = changes != null ? new LinkedHashSet<>(changes) : new LinkedHashSet<>();
        this.affectedAssetIds = assets != null ? new LinkedHashSet<>(assets) : new LinkedHashSet<>();
        this.rootCause = rootCause;
        this.workaround = workaround;
        this.resolution = resolution;
        this.reopenCount = reopenCount;
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Problem createNew(UUID id, UUID organisationId, String title, String description, Priority priority, Set<UUID> incidentIds,
                                    Set<UUID> changeIds, Set<UUID> assetIds, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for Problem creation.");
        }
        validate(title, description, priority);
        Set<UUID> incidents = links(incidentIds);
        Set<UUID> changes = links(changeIds);
        Set<UUID> assets = links(assetIds);
        requireExecutor(executor);
        return new Problem(id, organisationId, title, description, priority, incidents, changes, assets, executor);
    }

    public static Problem reconstitute(UUID id, UUID organisationId, String title, String description, Priority priority, ProblemStatus status,
                                       UUID assigneeId, Set<UUID> incidentIds, Set<UUID> changeIds, Set<UUID> assetIds, String rootCause,
                                       String workaround, String resolution, int reopenCount, List<Comment> comments, Instant createdAt,
                                       Instant updatedAt, List<ProblemAuditEntry> auditTrail) {
        if (id == null || organisationId == null || title == null || priority == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Title and Priority are mandatory to reconstitute a Problem.");
        }
        return new Problem(id, organisationId, title, description, priority, status, assigneeId, incidentIds, changeIds, assetIds, rootCause,
                workaround, resolution, reopenCount, comments, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. Title, description, priority and the links, while the problem is still open. */
    public ProblemAuditEntry updateDetails(String newTitle, String newDescription, Priority newPriority, Set<UUID> incidentIds, Set<UUID> changeIds,
                                           Set<UUID> assetIds, String executor) {
        requireStatus(ProblemStatus.NEW, ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR);
        validate(newTitle, newDescription, newPriority);
        Set<UUID> incidents = links(incidentIds);
        Set<UUID> changes = links(changeIds);
        Set<UUID> assets = links(assetIds);
        this.title = newTitle;
        this.description = newDescription;
        this.priority = newPriority;
        this.relatedIncidentIds = new LinkedHashSet<>(incidents);
        this.relatedChangeIds = new LinkedHashSet<>(changes);
        this.affectedAssetIds = new LinkedHashSet<>(assets);
        return record("UPDATED", executor, "Details updated (priority " + newPriority + ").");
    }

    /** Behavior Qualifier: {@code assignment/update}. Names who investigates; not a status change. */
    public ProblemAuditEntry assign(UUID newAssigneeId, String executor) {
        requireStatus(ProblemStatus.NEW, ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR);
        Objects.requireNonNull(newAssigneeId, "Assignee is mandatory to assign a Problem.");
        this.assigneeId = newAssigneeId;
        return record("ASSIGNED", executor, "Assigned to [" + newAssigneeId + "].");
    }

    /**
     * Behavior Qualifier: {@code analysis/update}. Records what the investigation found: the root cause and/or the workaround. A field left
     * {@code null} is kept as it was; one that is given must say something. Only while investigating or a known error.
     */
    public ProblemAuditEntry recordAnalysis(String newRootCause, String newWorkaround, String executor) {
        requireStatus(ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR);
        if (newRootCause == null && newWorkaround == null) {
            throw new IllegalArgumentException("The root cause or the workaround is needed to record an analysis.");
        }
        if ((newRootCause != null && newRootCause.isBlank()) || (newWorkaround != null && newWorkaround.isBlank())) {
            throw new IllegalArgumentException("A root cause or a workaround that is given cannot be blank.");
        }
        if (newRootCause != null) {
            this.rootCause = newRootCause;
        }
        if (newWorkaround != null) {
            this.workaround = newWorkaround;
        }
        return record("ANALYSIS_RECORDED", executor, newRootCause != null && newWorkaround != null ? "Root cause and workaround recorded."
                : newRootCause != null ? "Root cause recorded." : "Workaround recorded.");
    }

    /** Behavior Qualifier: {@code control/investigate}. NEW -&gt; UNDER_INVESTIGATION. */
    public ProblemAuditEntry investigate(String executor) {
        requireStatus(ProblemStatus.NEW);
        return transition(ProblemStatus.UNDER_INVESTIGATION, "INVESTIGATION_STARTED", executor, "Investigation started.");
    }

    /** Behavior Qualifier: {@code control/known-error}. UNDER_INVESTIGATION -&gt; KNOWN_ERROR: needs a root cause and a workaround on record. */
    public ProblemAuditEntry markKnownError(String executor) {
        requireStatus(ProblemStatus.UNDER_INVESTIGATION);
        if (isBlank(rootCause) || isBlank(workaround)) {
            throw new IllegalArgumentException("A known error needs both the root cause and a workaround recorded first.");
        }
        return transition(ProblemStatus.KNOWN_ERROR, "KNOWN_ERROR_DECLARED", executor, "Declared a known error: cause understood, workaround available.");
    }

    /** Behavior Qualifier: {@code control/resolve}. UNDER_INVESTIGATION or KNOWN_ERROR -&gt; RESOLVED: needs a root cause and the resolution. */
    public ProblemAuditEntry resolve(String resolutionNotes, String executor) {
        requireStatus(ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR);
        if (isBlank(rootCause)) {
            throw new IllegalArgumentException("The root cause must be recorded before a problem is resolved.");
        }
        if (isBlank(resolutionNotes)) {
            throw new IllegalArgumentException("Resolution notes (the permanent fix) are mandatory to resolve a Problem.");
        }
        this.resolution = resolutionNotes;
        return transition(ProblemStatus.RESOLVED, "RESOLVED", executor, "Resolved with a permanent fix.");
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    public ProblemAuditEntry close(String executor) {
        requireStatus(ProblemStatus.RESOLVED);
        return transition(ProblemStatus.CLOSED, "CLOSED", executor, "Closed.");
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; UNDER_INVESTIGATION: the fix did not hold; the reason is mandatory. */
    public ProblemAuditEntry reopen(String reason, String executor) {
        requireStatus(ProblemStatus.RESOLVED);
        if (isBlank(reason)) {
            throw new IllegalArgumentException("A reason is mandatory to reopen a Problem.");
        }
        this.reopenCount++;
        this.resolution = null;
        return transition(ProblemStatus.UNDER_INVESTIGATION, "REOPENED", executor, "Reopened: " + reason);
    }

    /** Behavior Qualifier: {@code control/cancel} (terminal, replaces DELETE). Before the problem is resolved. */
    public ProblemAuditEntry cancel(String executor) {
        requireStatus(ProblemStatus.NEW, ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR);
        return transition(ProblemStatus.CANCELLED, "CANCELLED", executor, "Cancelled.");
    }

    /** Behavior Qualifier: {@code comment/initiate}. Not available once the problem is closed or cancelled. */
    public ProblemAuditEntry addComment(Comment comment, String executor) {
        requireStatus(ProblemStatus.NEW, ProblemStatus.UNDER_INVESTIGATION, ProblemStatus.KNOWN_ERROR, ProblemStatus.RESOLVED);
        Objects.requireNonNull(comment, "comment is mandatory.");
        this.comments.add(comment);
        return record("COMMENT_ADDED", executor, "Comment added.");
    }

    // --- Internal helpers ---

    private ProblemAuditEntry transition(ProblemStatus newStatus, String action, String executor, String detail) {
        requireExecutor(executor);
        ProblemStatus previous = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        ProblemAuditEntry entry = new ProblemAuditEntry(this.updatedAt, action, executor, previous, newStatus, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private ProblemAuditEntry record(String action, String executor, String detail) {
        requireExecutor(executor);
        this.updatedAt = Instant.now();
        ProblemAuditEntry entry = new ProblemAuditEntry(this.updatedAt, action, executor, this.status, this.status, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(ProblemStatus... allowed) {
        if (Arrays.asList(allowed).contains(this.status)) {
            return;
        }
        throw new InvalidProblemStatusException(String.format(
                "Illegal transition: Problem is [%s], expected one of %s.", this.status, Arrays.toString(allowed)));
    }

    private static void validate(String title, String description, Priority priority) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is mandatory for a Problem.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is mandatory for a Problem.");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority is mandatory for a Problem.");
        }
    }

    private static Set<UUID> links(Set<UUID> ids) {
        Set<UUID> given = ids == null ? Set.of() : ids;
        if (given.size() > MAX_LINKS) {
            throw new IllegalArgumentException("A problem can link at most " + MAX_LINKS + " ids of each kind.");
        }
        return given;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Problem mutations.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public ProblemStatus getStatus() { return status; }
    public UUID getAssigneeId() { return assigneeId; }
    public Set<UUID> getRelatedIncidentIds() { return Collections.unmodifiableSet(relatedIncidentIds); }
    public Set<UUID> getRelatedChangeIds() { return Collections.unmodifiableSet(relatedChangeIds); }
    public Set<UUID> getAffectedAssetIds() { return Collections.unmodifiableSet(affectedAssetIds); }
    public String getRootCause() { return rootCause; }
    public String getWorkaround() { return workaround; }
    public String getResolution() { return resolution; }
    public int getReopenCount() { return reopenCount; }
    public List<Comment> getComments() { return Collections.unmodifiableList(comments); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProblemAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    /**
     * <pre>
     * NEW -&gt; UNDER_INVESTIGATION -&gt; KNOWN_ERROR -+
     *               |                              +-&gt; RESOLVED -&gt; CLOSED (terminal)     RESOLVED --reopen--&gt; UNDER_INVESTIGATION
     *               +------------------------------+
     * NEW, UNDER_INVESTIGATION, KNOWN_ERROR -&gt; CANCELLED (terminal)
     * </pre>
     */
    public enum ProblemStatus { NEW, UNDER_INVESTIGATION, KNOWN_ERROR, RESOLVED, CLOSED, CANCELLED }

    /** P1 is the most important. Chosen by the analyst, never derived (ADR-032). */
    public enum Priority { P1, P2, P3, P4 }

    /** Immutable forensic ledger entry, mirroring the platform's established audit-trail pattern. */
    public record ProblemAuditEntry(Instant occurredAt, String action, String executor,
                                    ProblemStatus fromStatus, ProblemStatus toStatus, String detail) {}

    public record Comment(UUID commentId, String author, String text, Instant createdAt) {
        public Comment {
            Objects.requireNonNull(commentId, "commentId cannot be null.");
            if (author == null || author.isBlank()) {
                throw new IllegalArgumentException("Comment author cannot be blank.");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Comment text cannot be blank.");
            }
            createdAt = createdAt != null ? createdAt : Instant.now();
        }
    }
}
