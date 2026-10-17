package com.example.tasks.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.tasks.TestSecurityConfig;
import com.example.tasks.domain.TaskStatus;
import com.example.tasks.dto.CreateTaskRequest;
import com.example.tasks.dto.PageResponse;
import com.example.tasks.dto.TaskResponse;
import com.example.tasks.dto.UpdateTaskRequest;
import com.example.tasks.service.TaskNotFoundException;
import com.example.tasks.service.TaskService;
import com.example.tasks.service.TaskValidationException;

@WebMvcTest(TaskController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(username = "alice")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    private static TaskResponse sample(long id) {
        return new TaskResponse(id, "Write tests", "Cover the API", TaskStatus.TODO, LocalDate.of(2999, 12, 31));
    }

    // ---------- create ----------

    @Test
    void createReturns201WithLocationHeaderAndBody() throws Exception {
        given(taskService.create(eq("alice"), any(CreateTaskRequest.class))).willReturn(sample(5L));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write tests","description":"Cover the API","status":"TODO","due_date":"2999-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/5")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.due_date").value("2999-12-31"));
    }

    @Test
    void createTakesOwnerFromSecurityContextAndIgnoresBodyOwner() throws Exception {
        given(taskService.create(eq("alice"), any(CreateTaskRequest.class))).willReturn(sample(6L));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Mine","owner":"mallory","owner_username":"mallory"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskService).create(eq("alice"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().title()).isEqualTo("Mine");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().status()).isNull();
    }

    @Test
    void createWithMissingTitleReturns400ProblemJsonWithErrors() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no title\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/tasks"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").exists());
        verifyNoInteractions(taskService);
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")));
    }

    @Test
    void createWithTooLongTitleReturns400() throws Exception {
        String title = "a".repeat(201);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void createWithTooLongDescriptionReturns400() throws Exception {
        String description = "d".repeat(2001);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ok\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void createWithPastDueDateReturns400() throws Exception {
        // The rule lives in TaskService (single clock); the controller must map its exception to problem+json.
        given(taskService.create(eq("alice"), any(CreateTaskRequest.class)))
                .willThrow(new TaskValidationException("due_date", "must not be in the past"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"old\",\"due_date\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("due_date"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be in the past"));
    }

    @Test
    void createWithUnparseableDueDateReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"due_date\":\"tomorrow\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("due_date"));
    }

    @Test
    void createWithUnknownStatusReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message", containsString("TODO")));
        verifyNoInteractions(taskService);
    }

    @Test
    void createWithMalformedJsonReturns400ProblemJson() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/tasks"));
        verifyNoInteractions(taskService);
    }

    // ---------- read ----------

    @Test
    void getReturns200() throws Exception {
        given(taskService.get("alice", 1L)).willReturn(sample(1L));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.due_date").value("2999-12-31"));
    }

    @Test
    void getUnknownTaskReturns404ProblemJson() throws Exception {
        given(taskService.get("alice", 99L)).willThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").value("Task Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("99")))
                .andExpect(jsonPath("$.instance").value("/api/tasks/99"));
    }

    @Test
    void getWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    // ---------- list / pagination ----------

    @Test
    void listUsesDefaultPaginationAndReturnsPageObject() throws Exception {
        given(taskService.list("alice", null, 0, 20))
                .willReturn(new PageResponse<>(List.of(sample(1L)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listPassesPageSizeAndStatusFilterToService() throws Exception {
        given(taskService.list("alice", TaskStatus.DONE, 2, 50))
                .willReturn(new PageResponse<>(List.of(), 2, 50, 0, 0));

        mockMvc.perform(get("/api/tasks")
                        .param("page", "2")
                        .param("size", "50")
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));

        verify(taskService).list("alice", TaskStatus.DONE, 2, 50);
    }

    @Test
    void listAcceptsMaximumPageSize() throws Exception {
        given(taskService.list("alice", null, 0, 100))
                .willReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/api/tasks").param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void listWithSizeAboveMaximumReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(taskService);
    }

    @Test
    void listWithZeroSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void listWithNegativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void listWithUnknownStatusFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        verifyNoInteractions(taskService);
    }

    // ---------- update ----------

    @Test
    void updateReturns200() throws Exception {
        given(taskService.update(eq("alice"), eq(3L), any(UpdateTaskRequest.class)))
                .willReturn(new TaskResponse(3L, "Renamed", null, TaskStatus.DONE, null));

        mockMvc.perform(put("/api/tasks/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void updateWithMissingStatusReturns400() throws Exception {
        mockMvc.perform(put("/api/tasks/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        verify(taskService, never()).update(any(), any(), any());
    }

    @Test
    void updateUnknownTaskReturns404() throws Exception {
        given(taskService.update(eq("alice"), eq(42L), any(UpdateTaskRequest.class)))
                .willThrow(new TaskNotFoundException(42L));

        mockMvc.perform(put("/api/tasks/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"TODO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- delete ----------

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/8"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(taskService).delete("alice", 8L);
    }

    @Test
    void deleteUnknownTaskReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new TaskNotFoundException(8L)).when(taskService).delete("alice", 8L);

        mockMvc.perform(delete("/api/tasks/8"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- other failures ----------

    @Test
    void unexpectedFailureReturns500WithoutLeakingDetails() throws Exception {
        given(taskService.get("alice", 1L)).willThrow(new IllegalStateException("secret internal failure"));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret internal failure"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void unsupportedMethodReturns405ProblemJson() throws Exception {
        mockMvc.perform(delete("/api/tasks"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    // ---------- authentication ----------

    @Test
    @WithAnonymousUser
    void listWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(taskService);
    }

    @Test
    @WithAnonymousUser
    void createWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(taskService);
    }

    @Test
    @WithAnonymousUser
    void deleteWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(taskService);
    }
}
