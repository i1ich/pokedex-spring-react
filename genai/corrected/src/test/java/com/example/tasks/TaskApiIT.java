package com.example.tasks;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Whole stack (controller, service, JPA, Flyway, PostgreSQL) with two users. This is the test that
 * proves the ownership rule end to end; the AI-generated tests only stub the repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class TaskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from tasks");
        jdbc.update("insert into users (username) values ('alice'), ('bob') on conflict do nothing");
    }

    private String createAs(String username, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/")))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void createThenReadBackRoundTrip() throws Exception {
        String id = createAs("alice", "{\"title\":\"Write docs\",\"description\":\"d\",\"due_date\":\"2999-12-31\"}");

        mockMvc.perform(get("/api/tasks/" + id).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write docs"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.due_date").value("2999-12-31"));
    }

    @Test
    void anotherUsersTaskLooksExactlyLikeAMissingOne() throws Exception {
        String id = createAs("alice", "{\"title\":\"private\"}");

        mockMvc.perform(get("/api/tasks/" + id).with(user("bob"))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/" + id).with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\",\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/" + id).with(user("bob"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/987654321").with(user("bob"))).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks/" + id).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("private"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void listShowsOnlyTheCallersTasks() throws Exception {
        createAs("alice", "{\"title\":\"a1\"}");
        createAs("alice", "{\"title\":\"a2\"}");
        createAs("bob", "{\"title\":\"b1\"}");

        mockMvc.perform(get("/api/tasks").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/tasks").with(user("bob")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("b1"));
    }

    @Test
    void ownerInTheBodyIsIgnored() throws Exception {
        String id = createAs("alice", "{\"title\":\"mine\",\"owner\":\"bob\",\"owner_username\":\"bob\"}");

        mockMvc.perform(get("/api/tasks/" + id).with(user("bob"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/" + id).with(user("alice"))).andExpect(status().isOk());
    }

    @Test
    void deleteThenGetIs404() throws Exception {
        String id = createAs("alice", "{\"title\":\"temp\"}");

        mockMvc.perform(delete("/api/tasks/" + id).with(user("alice"))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + id).with(user("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void pageSizeAboveTheCapIs400() throws Exception {
        mockMvc.perform(get("/api/tasks?size=101").with(user("alice"))).andExpect(status().isBadRequest());
    }
}
