package com.example.tasks.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.tasks.TestSecurityConfig;
import com.example.tasks.dto.PageResponse;
import com.example.tasks.service.TaskService;

/**
 * Request-parsing and content-negotiation cases the AI-generated controller tests did not cover.
 * Each test was written first and failed against the unmodified AI output (see genai/review.md).
 */
@WebMvcTest(TaskController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(username = "alice")
class TaskControllerHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void acceptHeaderWeCannotSatisfyIsA406NotA500() throws Exception {
        given(taskService.list("alice", null, 0, 20)).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get("/api/tasks").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void statusSentAsEnumOrdinalIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        verifyNoInteractions(taskService);
    }

    @Test
    void statusSentAsEnumOrdinalIsRejectedOnPutToo() throws Exception {
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        verifyNoInteractions(taskService);
    }

    @Test
    void titleSentAsJsonNumberIsRejectedInsteadOfCoercedToText() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2999-01-01T23:30:00Z", "2999-01-01T23:30:00", "2999-01-01T23:30:00-10:00"})
    void dueDateWithATimeComponentIsRejectedInsteadOfSilentlyTruncated(String value) throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"due_date\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("due_date"));
        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2999-01-01T23:30:00Z", "2999-01-01T23:30:00", "2999-01-01T23:30:00-10:00"})
    void dueDateWithATimeComponentIsRejectedOnPutToo(String value) throws Exception {
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"TODO\",\"due_date\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("due_date"));
        verifyNoInteractions(taskService);
    }

    @Test
    void impossibleCalendarDateIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"due_date\":\"2999-02-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("due_date"));
        verifyNoInteractions(taskService);
    }
}
