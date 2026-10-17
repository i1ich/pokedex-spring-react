package com.example.tasks.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tasks.TestSecurityConfig;
import com.example.tasks.domain.Task;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.service.TaskService;

/**
 * Controller and the real TaskService wired together over a fixed clock (2020-01-01 UTC), with only the
 * repository mocked. The "due date must not be in the past" rule has to follow the application Clock,
 * not the JVM wall clock, otherwise the boundary cannot be tested and depends on the host.
 */
@WebMvcTest(TaskController.class)
@Import({TestSecurityConfig.class, TaskService.class, DueDateClockTest.FixedClock.class})
@WithMockUser(username = "alice")
class DueDateClockTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2020-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskRepository taskRepository;

    @BeforeEach
    void echoSavedTask() {
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private ResultActions createWithDueDate(String date) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"due_date\":\"" + date + "\"}"));
    }

    @Test
    void dueDateAfterClockTodayIsAccepted() throws Exception {
        createWithDueDate("2020-06-01").andExpect(status().isCreated());
    }

    @Test
    void dueDateEqualToClockTodayIsAccepted() throws Exception {
        createWithDueDate("2020-01-01").andExpect(status().isCreated());
    }

    @Test
    void dueDateBeforeClockTodayIsRejectedWithTheServiceMessage() throws Exception {
        createWithDueDate("2019-12-31")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("due_date"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be in the past"));
    }
}
