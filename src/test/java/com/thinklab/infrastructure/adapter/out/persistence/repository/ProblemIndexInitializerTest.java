package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ProblemIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    private MongoCollection<Document> collectionIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("problems")).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates the four problem indexes in the database named by mongodb.uri, each matching a real query")
    void createsIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "tenant_prb");
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new ProblemIndexInitializer(client, "mongodb://mongo:27017/tenant_prb").onApplicationEvent(startup);

        ArgumentCaptor<Document> keys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection, times(4)).createIndex(keys.capture(), options.capture());
        assertEquals(List.of(
                new Document("organisationId", 1).append("status", 1).append("priority", 1),
                new Document("organisationId", 1).append("assigneeId", 1),
                new Document("organisationId", 1).append("relatedIncidentIds", 1),
                new Document("organisationId", 1).append("affectedAssetIds", 1)), keys.getAllValues());
        assertEquals(List.of(ProblemIndexInitializer.QUEUE_INDEX, ProblemIndexInitializer.ASSIGNEE_INDEX,
                ProblemIndexInitializer.INCIDENT_INDEX, ProblemIndexInitializer.ASSET_INDEX), options.getAllValues().stream().map(IndexOptions::getName).toList());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, ProblemMongoRepositoryAdapter.DEFAULT_DATABASE);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new ProblemIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "prb_db");
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected")))
                .thenReturn(Mono.just("ok"));

        assertDoesNotThrow(() -> new ProblemIndexInitializer(client, "mongodb://mongo:27017/prb_db", Duration.ofSeconds(1)).onApplicationEvent(startup));
        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new ProblemIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new ProblemIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new ProblemIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
