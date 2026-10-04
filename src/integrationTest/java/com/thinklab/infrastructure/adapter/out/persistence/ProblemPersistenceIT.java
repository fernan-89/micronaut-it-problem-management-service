package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.InvalidProblemStatusException;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.domain.repository.ProblemRepository;
import com.thinklab.domain.repository.ProblemRepository.Filter;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Problem aggregate through its repository against a real MongoDB: the guarded save (a second writer from the same state loses),
 * tenant scoping, every collection filter (including "which problems explain this incident"), comments, and the four indexes
 * {@link com.thinklab.infrastructure.adapter.out.persistence.repository.ProblemIndexInitializer} creates at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProblemPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "problem_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    ProblemRepository problems;

    @Inject
    MongoClient mongoClient;

    private Problem newProblem(UUID organisation, Priority priority, Set<UUID> incidents, Set<UUID> assets) {
        return Problem.createNew(UUID.randomUUID(), organisation, "Switch drops packets", "Intermittent", priority, incidents, null, assets, EXECUTOR);
    }

    private static Set<UUID> ids(List<Problem> found) {
        return found.stream().map(Problem::getId).collect(Collectors.toSet());
    }

    private List<Problem> list(UUID organisation, Filter filter) {
        return problems.findAll(organisation, filter).collectList().block();
    }

    @Test
    @DisplayName("a created problem is read back whole: links and its INITIATED audit entry")
    void createAndFind() {
        UUID organisation = UUID.randomUUID();
        UUID incident = UUID.randomUUID();
        Problem created = problems.create(newProblem(organisation, Priority.P2, Set.of(incident), Set.of())).block();

        Problem found = problems.findById(created.getId(), organisation).block();

        assertEquals(ProblemStatus.NEW, found.getStatus());
        assertEquals(Priority.P2, found.getPriority());
        assertEquals(Set.of(incident), found.getRelatedIncidentIds());
        assertEquals(1, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("another organisation's problem is simply not found")
    void tenantIsolation() {
        UUID organisation = UUID.randomUUID();
        Problem created = problems.create(newProblem(organisation, Priority.P3, null, null)).block();

        assertNull(problems.findById(created.getId(), UUID.randomUUID()).block());
        assertTrue(list(UUID.randomUUID(), new Filter(null, null, null, null, null, false)).isEmpty());
    }

    @Test
    @DisplayName("save persists the whole state the aggregate reached, the analysis included, with its audit entry")
    void saveAppliesTheTransition() {
        UUID organisation = UUID.randomUUID();
        Problem created = problems.create(newProblem(organisation, Priority.P2, null, null)).block();
        Problem loaded = problems.findById(created.getId(), organisation).block();
        var investigation = loaded.investigate(EXECUTOR);
        problems.save(loaded, ProblemStatus.NEW, investigation).block();
        var analysis = loaded.recordAnalysis("Bad firmware", "Reboot weekly", EXECUTOR);
        problems.save(loaded, ProblemStatus.UNDER_INVESTIGATION, analysis).block();
        var known = loaded.markKnownError(EXECUTOR);
        problems.save(loaded, ProblemStatus.UNDER_INVESTIGATION, known).block();

        Problem found = problems.findById(created.getId(), organisation).block();

        assertEquals(ProblemStatus.KNOWN_ERROR, found.getStatus());
        assertEquals("Bad firmware", found.getRootCause());
        assertEquals("Reboot weekly", found.getWorkaround());
        assertEquals(4, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("two people working the same problem from the same state cannot both win: the second save is refused, not merged")
    void concurrentWritersAreGuarded() {
        UUID organisation = UUID.randomUUID();
        Problem created = problems.create(newProblem(organisation, Priority.P2, null, null)).block();
        Problem seenByA = problems.findById(created.getId(), organisation).block();
        Problem seenByB = problems.findById(created.getId(), organisation).block();
        var entryA = seenByA.investigate("op-a");
        var entryB = seenByB.cancel("op-b");

        problems.save(seenByA, ProblemStatus.NEW, entryA).block();

        assertThrows(InvalidProblemStatusException.class, () -> problems.save(seenByB, ProblemStatus.NEW, entryB).block());
        Problem found = problems.findById(created.getId(), organisation).block();
        assertEquals(ProblemStatus.UNDER_INVESTIGATION, found.getStatus());
        assertEquals(2, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("addComment pushes the comment and its audit entry; an unknown problem is ProblemNotFoundException")
    void comments() {
        UUID organisation = UUID.randomUUID();
        Problem created = problems.create(newProblem(organisation, Priority.P4, null, null)).block();
        var comment = new Comment(UUID.randomUUID(), EXECUTOR, "Checked the logs", null);
        var entry = created.addComment(comment, EXECUTOR);

        problems.addComment(created.getId(), organisation, comment, entry).block();

        Problem found = problems.findById(created.getId(), organisation).block();
        assertEquals(1, found.getComments().size());
        assertEquals(2, found.getAuditTrail().size());
        assertThrows(ProblemNotFoundException.class, () -> problems.addComment(UUID.randomUUID(), organisation, comment, entry).block());
        assertThrows(ProblemNotFoundException.class, () -> problems.addComment(created.getId(), UUID.randomUUID(), comment, entry).block());
    }

    @Test
    @DisplayName("the collection honours status, priority, assignee, incident, asset and open-only, always inside the tenant")
    void filters() {
        UUID organisation = UUID.randomUUID();
        UUID incident = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Problem open = problems.create(newProblem(organisation, Priority.P3, Set.of(incident), Set.of(asset))).block();
        Problem critical = problems.create(newProblem(organisation, Priority.P1, null, null)).block();
        Problem cancelled = problems.create(newProblem(organisation, Priority.P3, null, null)).block();
        problems.create(newProblem(UUID.randomUUID(), Priority.P3, Set.of(incident), Set.of(asset))).block();
        var assignment = open.assign(assignee, EXECUTOR);
        problems.save(open, ProblemStatus.NEW, assignment).block();
        var cancellation = cancelled.cancel(EXECUTOR);
        problems.save(cancelled, ProblemStatus.NEW, cancellation).block();

        assertEquals(Set.of(open.getId(), critical.getId(), cancelled.getId()), ids(list(organisation, new Filter(null, null, null, null, null, false))));
        assertEquals(Set.of(open.getId(), critical.getId()), ids(list(organisation, new Filter(null, null, null, null, null, true))));
        assertEquals(Set.of(cancelled.getId()), ids(list(organisation, new Filter(ProblemStatus.CANCELLED, null, null, null, null, false))));
        assertEquals(Set.of(critical.getId()), ids(list(organisation, new Filter(null, Priority.P1, null, null, null, false))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, null, assignee, null, null, false))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, null, null, incident, null, false))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, null, null, null, asset, false))));
    }

    @Test
    @DisplayName("the four compound indexes exist on problems")
    void indexesExist() {
        problems.create(newProblem(UUID.randomUUID(), Priority.P2, null, null)).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("problems").listIndexes()).collectList().block();
        Set<Document> keys = indexes.stream().map(index -> index.get("key", Document.class)).collect(Collectors.toSet());

        assertTrue(keys.contains(new Document("organisationId", 1).append("status", 1).append("priority", 1)), () -> "problems: " + indexes);
        assertTrue(keys.contains(new Document("organisationId", 1).append("assigneeId", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("relatedIncidentIds", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("affectedAssetIds", 1)));
    }
}
