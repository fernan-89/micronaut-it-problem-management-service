package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.InvalidProblemStatusException;
import com.thinklab.domain.exception.ProblemNotFoundException;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.ProblemAuditEntry;
import com.thinklab.domain.model.Problem.ProblemStatus;
import com.thinklab.domain.repository.ProblemRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument.CommentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ProblemDocument.ProblemPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the Problem aggregate, raw reactive-streams driver. Every change is a single atomic
 * {@code $set}/{@code $push} that also appends the forensic audit entry, so state and ledger can never diverge, and every filter carries
 * the organisation: another tenant's problem is simply not found.
 */
@Singleton
public class ProblemMongoRepositoryAdapter implements ProblemRepository {

    private static final Logger log = LoggerFactory.getLogger(ProblemMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_problem_management_db";
    static final String COLLECTION_NAME = "problems";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ProblemMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<ProblemDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ProblemDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Problem> create(Problem problem) {
        log.debug("[PERSISTENCE] Monolithic create for Problem Aggregate: {}", problem.getId());

        return Mono.from(getCollection().insertOne(ProblemPersistenceMapper.toDocument(problem))).map(result -> problem);
    }

    @Override
    public Mono<Problem> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(ProblemPersistenceMapper::toDomain);
    }

    @Override
    public Flux<Problem> findAll(UUID organisationId, Filter filter) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (filter.status() != null) {
            filters.add(Filters.eq(FIELD_STATUS, filter.status().name()));
        }
        if (filter.priority() != null) {
            filters.add(Filters.eq("priority", filter.priority().name()));
        }
        if (filter.assigneeId() != null) {
            filters.add(Filters.eq("assigneeId", filter.assigneeId()));
        }
        if (filter.incidentId() != null) {
            filters.add(Filters.eq("relatedIncidentIds", filter.incidentId()));
        }
        if (filter.assetId() != null) {
            filters.add(Filters.eq("affectedAssetIds", filter.assetId()));
        }
        if (filter.openOnly()) {
            filters.add(Filters.nin(FIELD_STATUS, ProblemStatus.RESOLVED.name(), ProblemStatus.CLOSED.name(), ProblemStatus.CANCELLED.name()));
        }

        return Flux.from(getCollection().find(Filters.and(filters))).map(ProblemPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Problem problem, ProblemStatus expectedStatus, ProblemAuditEntry auditEntry) {
        // Guarded write: it only applies while the problem still has the status it had when it was loaded.
        Bson guard = Filters.and(Filters.eq(FIELD_ID, problem.getId()), Filters.eq(FIELD_ORGANISATION, problem.getOrganisationId()),
                Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("title", problem.getTitle()),
                Updates.set("description", problem.getDescription()),
                Updates.set("priority", problem.getPriority().name()),
                Updates.set(FIELD_STATUS, problem.getStatus().name()),
                Updates.set("assigneeId", problem.getAssigneeId()),
                Updates.set("relatedIncidentIds", new ArrayList<>(problem.getRelatedIncidentIds())),
                Updates.set("relatedChangeIds", new ArrayList<>(problem.getRelatedChangeIds())),
                Updates.set("affectedAssetIds", new ArrayList<>(problem.getAffectedAssetIds())),
                Updates.set("rootCause", problem.getRootCause()),
                Updates.set("workaround", problem.getWorkaround()),
                Updates.set("resolution", problem.getResolution()),
                Updates.set("reopenCount", problem.getReopenCount()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidProblemStatusException("Problem was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty());
    }

    @Override
    public Mono<Void> addComment(UUID id, UUID organisationId, Comment comment, ProblemAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.push("comments", CommentDocument.fromDomain(comment)),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId)), update))
                .flatMap(result -> result.getMatchedCount() == 0 ? Mono.error(new ProblemNotFoundException(id)) : Mono.<Void>empty());
    }
}
