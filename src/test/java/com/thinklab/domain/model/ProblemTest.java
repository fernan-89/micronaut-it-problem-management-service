package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidProblemStatusException;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemTest {

    private final UUID org = UUID.randomUUID();

    private Problem problem() {
        return Problem.createNew(UUID.randomUUID(), org, "Switch drops packets", "Intermittent, floor 3", Priority.P2, Set.of(UUID.randomUUID()), null, null, "op-1");
    }

    private Problem investigating() {
        Problem problem = problem();
        problem.investigate("op-1");
        return problem;
    }

    @Test
    @DisplayName("a new problem is NEW with an INITIATED audit entry and its links as references")
    void createNew() {
        UUID incident = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        UUID asset = UUID.randomUUID();

        Problem problem = Problem.createNew(UUID.randomUUID(), org, "t", "d", Priority.P1, Set.of(incident), Set.of(change), Set.of(asset), "op-1");

        assertEquals(ProblemStatus.NEW, problem.getStatus());
        assertEquals(Set.of(incident), problem.getRelatedIncidentIds());
        assertEquals(Set.of(change), problem.getRelatedChangeIds());
        assertEquals(Set.of(asset), problem.getAffectedAssetIds());
        assertEquals(1, problem.getAuditTrail().size());
        assertNull(problem.getAuditTrail().get(0).fromStatus());
        assertEquals(0, problem.getReopenCount());
    }

    @Test
    @DisplayName("creation refuses missing ids, a blank title or description, no priority, too many links and a blank executor")
    void createGuards() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(null, org, "t", "d", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, null, "t", "d", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, null, "d", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, " ", "d", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", null, Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", " ", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", "d", null, null, null, null, "op"));
        Set<UUID> tooMany = new HashSet<>();
        for (int i = 0; i <= Problem.MAX_LINKS; i++) {
            tooMany.add(UUID.randomUUID());
        }
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", "d", Priority.P1, tooMany, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", "d", Priority.P1, null, null, null, " "));
        assertThrows(IllegalArgumentException.class, () -> Problem.createNew(id, org, "t", "d", Priority.P1, null, null, null, null));
    }

    @Test
    @DisplayName("details and links are replaced while the problem is open, and validated like on creation; not once it is resolved")
    void update() {
        Problem problem = problem();
        UUID incident = UUID.randomUUID();

        var entry = problem.updateDetails("New title", "New description", Priority.P1, Set.of(incident), Set.of(), Set.of(), "op-2");

        assertEquals("UPDATED", entry.action());
        assertEquals(Priority.P1, problem.getPriority());
        assertEquals(Set.of(incident), problem.getRelatedIncidentIds());
        assertThrows(IllegalArgumentException.class, () -> problem.updateDetails(" ", "d", Priority.P1, null, null, null, "op"));
        assertThrows(IllegalArgumentException.class, () -> problem.updateDetails("t", "d", Priority.P1, null, null, null, " "));

        Problem resolved = investigating();
        resolved.recordAnalysis("Bad firmware", null, "op-1");
        resolved.resolve("Upgraded", "op-1");
        assertThrows(InvalidProblemStatusException.class, () -> resolved.updateDetails("t", "d", Priority.P1, null, null, null, "op"));
    }

    @Test
    @DisplayName("assign names who investigates, without changing the status; the assignee is mandatory and a closed problem refuses")
    void assign() {
        Problem problem = problem();
        UUID assignee = UUID.randomUUID();

        var entry = problem.assign(assignee, "op-1");

        assertEquals(assignee, problem.getAssigneeId());
        assertEquals(ProblemStatus.NEW, entry.toStatus());
        assertThrows(NullPointerException.class, () -> problem.assign(null, "op-1"));
        problem.cancel("op-1");
        assertThrows(InvalidProblemStatusException.class, () -> problem.assign(assignee, "op-1"));
    }

    @Test
    @DisplayName("an analysis records the root cause and/or the workaround; a missing or blank one is refused, only while investigating")
    void analysis() {
        Problem problem = problem();
        assertThrows(InvalidProblemStatusException.class, () -> problem.recordAnalysis("cause", null, "op-1"));
        problem.investigate("op-1");

        assertEquals("Root cause recorded.", problem.recordAnalysis("Bad firmware", null, "op-1").detail());
        assertEquals("Workaround recorded.", problem.recordAnalysis(null, "Reboot the switch", "op-1").detail());
        assertEquals("Root cause and workaround recorded.", problem.recordAnalysis("Bad firmware 2.1", "Reboot weekly", "op-1").detail());
        assertEquals("Bad firmware 2.1", problem.getRootCause());
        assertEquals("Reboot weekly", problem.getWorkaround());
        assertThrows(IllegalArgumentException.class, () -> problem.recordAnalysis(null, null, "op-1"));
        assertThrows(IllegalArgumentException.class, () -> problem.recordAnalysis(" ", null, "op-1"));
        assertThrows(IllegalArgumentException.class, () -> problem.recordAnalysis("cause", " ", "op-1"));
    }

    @Test
    @DisplayName("NEW -> UNDER_INVESTIGATION -> KNOWN_ERROR needs both a root cause and a workaround on record")
    void knownError() {
        Problem problem = problem();
        assertThrows(InvalidProblemStatusException.class, () -> problem.markKnownError("op-1"));
        problem.investigate("op-1");
        assertThrows(InvalidProblemStatusException.class, () -> problem.investigate("op-1"));

        assertThrows(IllegalArgumentException.class, () -> problem.markKnownError("op-1"));
        problem.recordAnalysis("Bad firmware", null, "op-1");
        assertThrows(IllegalArgumentException.class, () -> problem.markKnownError("op-1"));
        problem.recordAnalysis(null, "Reboot the switch", "op-1");
        var entry = problem.markKnownError("op-1");

        assertEquals(ProblemStatus.KNOWN_ERROR, problem.getStatus());
        assertEquals(ProblemStatus.UNDER_INVESTIGATION, entry.fromStatus());
        assertThrows(InvalidProblemStatusException.class, () -> problem.markKnownError("op-1"));
    }

    @Test
    @DisplayName("resolve needs the root cause and the resolution, works from UNDER_INVESTIGATION and from KNOWN_ERROR, and close needs RESOLVED")
    void resolve() {
        Problem direct = investigating();
        assertThrows(IllegalArgumentException.class, () -> direct.resolve("fix", "op-1"));
        direct.recordAnalysis("Bad firmware", null, "op-1");
        assertThrows(IllegalArgumentException.class, () -> direct.resolve(null, "op-1"));
        assertThrows(IllegalArgumentException.class, () -> direct.resolve(" ", "op-1"));
        assertThrows(InvalidProblemStatusException.class, () -> direct.close("op-1"));
        direct.resolve("Upgraded to 2.2", "op-1");
        assertEquals(ProblemStatus.RESOLVED, direct.getStatus());
        assertEquals("Upgraded to 2.2", direct.getResolution());
        direct.close("op-1");
        assertEquals(ProblemStatus.CLOSED, direct.getStatus());
        assertThrows(InvalidProblemStatusException.class, () -> direct.cancel("op-1"));

        Problem known = investigating();
        known.recordAnalysis("Bad firmware", "Reboot", "op-1");
        known.markKnownError("op-1");
        known.resolve("Upgraded", "op-1");
        assertEquals(ProblemStatus.KNOWN_ERROR, known.getAuditTrail().get(known.getAuditTrail().size() - 1).fromStatus());
    }

    @Test
    @DisplayName("a resolved problem is reopened with a reason (the fix is cleared, the count goes up); only from RESOLVED")
    void reopen() {
        Problem problem = investigating();
        assertThrows(InvalidProblemStatusException.class, () -> problem.reopen("again", "op-1"));
        problem.recordAnalysis("Bad firmware", null, "op-1");
        problem.resolve("Upgraded", "op-1");
        assertThrows(IllegalArgumentException.class, () -> problem.reopen(" ", "op-1"));
        assertThrows(IllegalArgumentException.class, () -> problem.reopen(null, "op-1"));

        var entry = problem.reopen("Still dropping packets", "op-1");

        assertEquals(ProblemStatus.UNDER_INVESTIGATION, problem.getStatus());
        assertEquals(1, problem.getReopenCount());
        assertNull(problem.getResolution());
        assertTrue(entry.detail().contains("Still dropping packets"));
    }

    @Test
    @DisplayName("cancel works until the problem is resolved; comments work until it is closed or cancelled")
    void cancelAndComments() {
        Problem problem = problem();
        var comment = new Comment(UUID.randomUUID(), "op-1", "Checked the logs", null);

        var entry = problem.addComment(comment, "op-1");

        assertEquals(1, problem.getComments().size());
        assertEquals("COMMENT_ADDED", entry.action());
        assertThrows(NullPointerException.class, () -> problem.addComment(null, "op-1"));
        assertThrows(NullPointerException.class, () -> new Comment(null, "a", "t", null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), " ", "t", null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), null, "t", null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "a", " ", null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "a", null, null));
        assertEquals(Instant.parse("2026-10-04T10:00:00Z"), new Comment(UUID.randomUUID(), "a", "t", Instant.parse("2026-10-04T10:00:00Z")).createdAt());

        problem.cancel("op-1");
        assertEquals(ProblemStatus.CANCELLED, problem.getStatus());
        assertThrows(InvalidProblemStatusException.class, () -> problem.addComment(comment, "op-1"));
        assertThrows(InvalidProblemStatusException.class, () -> problem.cancel("op-1"));
    }

    @Test
    @DisplayName("every mutation needs an executor")
    void executorRequired() {
        assertThrows(IllegalArgumentException.class, () -> problem().investigate(" "));
        assertThrows(IllegalArgumentException.class, () -> problem().assign(UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("reconstitute needs the identity and defaults the optional state")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Problem.reconstitute(null, org, "t", null, Priority.P1, null, null, null, null, null, null, null, null, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Problem.reconstitute(id, null, "t", null, Priority.P1, null, null, null, null, null, null, null, null, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Problem.reconstitute(id, org, null, null, Priority.P1, null, null, null, null, null, null, null, null, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Problem.reconstitute(id, org, "t", null, null, null, null, null, null, null, null, null, null, 0, null, null, null, null));

        Problem bare = Problem.reconstitute(id, org, "t", null, Priority.P3, null, null, null, null, null, null, null, null, 0, null, null, null, null);
        assertEquals(ProblemStatus.NEW, bare.getStatus());
        assertTrue(bare.getRelatedIncidentIds().isEmpty());
        assertTrue(bare.getComments().isEmpty());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());

        Problem source = problem();
        Problem full = Problem.reconstitute(source.getId(), org, "t", "d", Priority.P2, ProblemStatus.KNOWN_ERROR, UUID.randomUUID(), Set.of(UUID.randomUUID()),
                Set.of(), Set.of(), "cause", "workaround", "fix", 2, List.of(), source.getCreatedAt(), source.getUpdatedAt(), source.getAuditTrail());
        assertEquals(ProblemStatus.KNOWN_ERROR, full.getStatus());
        assertEquals(2, full.getReopenCount());
        assertEquals(source.getUpdatedAt(), full.getUpdatedAt());
        assertEquals(1, full.getAuditTrail().size());
    }
}
