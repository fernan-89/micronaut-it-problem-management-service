package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.InvalidProblemStatusException;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.domain.repository.ProblemRepository.Filter;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument.ProblemPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ProblemMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ProblemDocument> mongoCollection;

    private ProblemMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private Problem problem;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_it_problem_management_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("problems", ProblemDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new ProblemMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_it_problem_management_db");
        organisationId = UUID.randomUUID();
        problem = Problem.createNew(UUID.randomUUID(), organisationId, "Switch drops packets", "Intermittent", Priority.P2, Set.of(UUID.randomUUID()), null, null, "op-1");
    }

    private void finds(Problem... found) {
        FindPublisher<ProblemDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        lenient().when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ProblemDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(ProblemPersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("the database falls back to the service default when the URI has none")
    void databaseFallback() {
        ProblemMongoRepositoryAdapter fallback = new ProblemMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(ProblemDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallback.create(problem)).expectNext(problem).verifyComplete();
    }

    @Test
    @DisplayName("findById is scoped to the organisation and maps the document back to the aggregate")
    void findById() {
        finds(problem);

        StepVerifier.create(adapter.findById(problem.getId(), organisationId)).assertNext(found -> assertEquals(problem.getId(), found.getId())).verifyComplete();
    }

    @Test
    @DisplayName("findAll applies every optional filter, and the open-only exclusion")
    void findAll() {
        finds(problem);
        UUID anyId = UUID.randomUUID();

        StepVerifier.create(adapter.findAll(organisationId, new Filter(ProblemStatus.NEW, Priority.P2, anyId, anyId, anyId, true))).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(organisationId, new Filter(null, null, null, null, null, false))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, times(2)).find(filter.capture());
        String full = filter.getAllValues().get(0).toString();
        assertTrue(full.contains("priority") && full.contains("assigneeId") && full.contains("relatedIncidentIds") && full.contains("affectedAssetIds") && full.contains("CLOSED"));
        String bare = filter.getAllValues().get(1).toString();
        assertTrue(bare.contains("organisationId") && !bare.contains("priority") && !bare.contains("CLOSED"));
    }

    @Test
    @DisplayName("save is a guarded write on the organisation and the status loaded; a lost race is a 409-style conflict")
    void save() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        var entry = problem.investigate("op-1");

        StepVerifier.create(adapter.save(problem, ProblemStatus.NEW, entry)).verifyComplete();
        StepVerifier.create(adapter.save(problem, ProblemStatus.NEW, entry)).expectError(InvalidProblemStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, times(2)).updateOne(guard.capture(), any(Bson.class));
        assertTrue(guard.getAllValues().get(0).toString().contains("organisationId") && guard.getAllValues().get(0).toString().contains("NEW"));
    }

    @Test
    @DisplayName("addComment pushes the comment and the audit entry; an unknown problem is not found")
    void addComment() {
        var comment = new Comment(UUID.randomUUID(), "op-1", "Checked", null);
        var entry = problem.addComment(comment, "op-1");
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.addComment(problem.getId(), organisationId, comment, entry)).verifyComplete();
        StepVerifier.create(adapter.addComment(problem.getId(), organisationId, comment, entry)).expectError(ProblemNotFoundException.class).verify();
    }
}
