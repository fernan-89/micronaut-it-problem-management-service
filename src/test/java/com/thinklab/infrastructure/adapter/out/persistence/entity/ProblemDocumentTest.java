package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument.ProblemPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemDocumentTest {

    @Test
    @DisplayName("a problem that went through its life survives storage: links, analysis, reopen count, comments and the audit trail")
    void roundTrip() {
        UUID incident = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Problem problem = Problem.createNew(UUID.randomUUID(), UUID.randomUUID(), "Switch drops packets", "Intermittent", Priority.P2, Set.of(incident),
                Set.of(change), Set.of(asset), "op-1");
        problem.assign(assignee, "op-1");
        problem.investigate("op-1");
        problem.recordAnalysis("Bad firmware", "Reboot weekly", "op-1");
        problem.markKnownError("op-1");
        problem.addComment(new Comment(UUID.randomUUID(), "op-1", "Checked the logs", null), "op-1");
        problem.resolve("Upgraded", "op-1");
        problem.reopen("Not fixed", "op-1");
        problem.resolve("Upgraded again", "op-1");

        ProblemDocument document = ProblemPersistenceMapper.toDocument(problem);
        Problem restored = ProblemPersistenceMapper.toDomain(document);

        assertEquals(problem.getId(), restored.getId());
        assertEquals(ProblemStatus.RESOLVED, restored.getStatus());
        assertEquals(Priority.P2, restored.getPriority());
        assertEquals(assignee, restored.getAssigneeId());
        assertEquals(Set.of(incident), restored.getRelatedIncidentIds());
        assertEquals(Set.of(change), restored.getRelatedChangeIds());
        assertEquals(Set.of(asset), restored.getAffectedAssetIds());
        assertEquals("Bad firmware", restored.getRootCause());
        assertEquals("Reboot weekly", restored.getWorkaround());
        assertEquals("Upgraded again", restored.getResolution());
        assertEquals(1, restored.getReopenCount());
        assertEquals(1, restored.getComments().size());
        assertEquals(problem.getAuditTrail().size(), restored.getAuditTrail().size());
        assertNull(restored.getAuditTrail().get(0).fromStatus());
        assertEquals(ProblemStatus.NEW, restored.getAuditTrail().get(1).fromStatus());
        assertEquals(problem.getCreatedAt(), document.getCreatedAt());
        assertEquals(problem.getUpdatedAt(), document.getUpdatedAt());
        assertEquals("op-1", document.getAuditTrail().get(0).executor());
        assertEquals("Switch drops packets", document.getTitle());
        assertEquals("Intermittent", document.getDescription());
        assertEquals(problem.getOrganisationId(), document.getOrganisationId());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<ProblemPersistenceMapper> constructor = ProblemPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
