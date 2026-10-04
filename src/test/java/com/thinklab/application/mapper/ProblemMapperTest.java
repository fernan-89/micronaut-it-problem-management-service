package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateProblemRequest;
import com.thinklab.application.dto.response.ProblemResponse;
import com.thinklab.domain.model.Problem;
import com.thinklab.domain.model.Problem.Comment;
import com.thinklab.domain.model.Problem.Priority;
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

class ProblemMapperTest {

    @Test
    @DisplayName("a request becomes a NEW problem, and the problem maps back with its links, comments and audit entries")
    void mapping() {
        UUID incident = UUID.randomUUID();
        var request = new InitiateProblemRequest("Switch drops packets", "Intermittent", Priority.P2, Set.of(incident), null, null);

        Problem problem = ProblemMapper.toDomain(request, UUID.randomUUID(), UUID.randomUUID(), "op-1");
        problem.addComment(new Comment(UUID.randomUUID(), "op-1", "Checked", null), "op-1");
        ProblemResponse response = ProblemMapper.toResponse(problem);

        assertEquals("NEW", response.status());
        assertEquals("P2", response.priority());
        assertEquals(Set.of(incident), response.relatedIncidentIds());
        assertEquals(1, response.comments().size());
        assertNull(ProblemMapper.toResponse(problem.getAuditTrail().get(0)).fromStatus());
        assertEquals("NEW", ProblemMapper.toResponse(problem.getAuditTrail().get(1)).fromStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<ProblemMapper> constructor = ProblemMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
