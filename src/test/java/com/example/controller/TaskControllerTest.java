package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskNotFoundException;
import com.example.model.Role;
import com.example.model.Status;
import com.example.model.User;
import com.example.security.JwtService;
import com.example.security.SecurityExceptionHandler;
import com.example.service.TaskService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты веб-слоя: @WebMvcTest поднимает только контроллер, советы (@ControllerAdvice) и фильтры.
 * Бизнес-логика замокана (@MockitoBean TaskService). Правила безопасности подключены явно (@Import) -
 * без этого в срезе работала бы защита Spring Boot по умолчанию, а не наша.
 * JwtAuthFilter - настоящий; ему нужны JwtService и UserDetailsService - они тоже замоканы.
 */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, SecurityExceptionHandler.class})
class TaskControllerTest {

    private static final String BASE = "/api/tasks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ---------- 401: без токена ----------

    @Test
    void getAll_whenNotAuthenticated_shouldReturn401Json() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized")); // JSON, а не HTML-страница
    }

    @Test
    void create_whenNotAuthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskService);
    }

    // ---------- чтение ----------

    @Test
    @WithMockUser(roles = "USER")
    void getAll_whenAuthenticated_shouldReturn200WithPage() throws Exception {
        when(taskService.getAll(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response(1L, "Buy milk"))));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Buy milk"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAll_shouldAlsoWorkUnderShortPrefix() throws Exception {
        when(taskService.getAll(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/tasks")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getById_whenNotFound_shouldReturn404Json() throws Exception {
        when(taskService.getOrThrow(eq(999L), any())).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get(BASE + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found"))
                .andExpect(jsonPath("$.message").value("Задача с id=999 не найдена"));
    }

    @Test
    void getMy_shouldPassCurrentUserFromSecurityContextToService() throws Exception {
        User alice = buildUser(1L, "alice@test.com");
        when(taskService.getMyTasks(alice)).thenReturn(List.of(response(1L, "Alice task")));

        mockMvc.perform(get(BASE + "/my").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Alice task"));

        verify(taskService).getMyTasks(alice); // @AuthenticationPrincipal отдал именно нашего User
    }

    // ---------- создание ----------

    @Test
    @WithMockUser(roles = "USER")
    void create_whenValid_shouldReturn201WithLocation() throws Exception {
        when(taskService.create(any(TaskRequest.class), any())).thenReturn(response(1L, "Buy milk"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Buy milk\",\"priority\":\"high\"}")) // enum в любом регистре
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tasks/1")))
                .andExpect(jsonPath("$.title").value("Buy milk"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_whenTitleBlank_shouldReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_whenPriorityUnknown_shouldReturn400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    // ---------- удаление: ADMIN или владелец ----------

    @Test
    @WithMockUser(roles = "USER")
    void delete_whenRoleUserAndNotOwner_shouldReturn403Json() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(taskService, never()).delete(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenRoleAdmin_shouldReturn204() throws Exception {
        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isNoContent());

        verify(taskService).delete(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "alice@test.com", roles = "USER")
    void delete_whenOwner_shouldReturn204() throws Exception {
        // SpEL @taskService.isOwner(#id, authentication.name) получает id из URL и email из аутентификации
        when(taskService.isOwner(1L, "alice@test.com")).thenReturn(true);

        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isNoContent());

        verify(taskService).delete(eq(1L), any());
    }

    // ---------- обновление ----------

    @Test
    @WithMockUser(username = "other@test.com", roles = "USER")
    void update_whenNotOwner_shouldReturn403() throws Exception {
        mockMvc.perform(put(BASE + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        verify(taskService, never()).update(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_whenAdminAndTaskMissing_shouldReturn404() throws Exception {
        when(taskService.update(eq(5L), any(TaskRequest.class), any())).thenReturn(Optional.empty());

        mockMvc.perform(put(BASE + "/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_whenValid_shouldReturn200() throws Exception {
        when(taskService.updateStatus(eq(1L), eq(Status.DONE), any()))
                .thenReturn(Optional.of(response(1L, "Buy milk")));

        mockMvc.perform(patch(BASE + "/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_whenStatusMissing_shouldReturn400() throws Exception {
        mockMvc.perform(patch(BASE + "/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    // ---------- JwtAuthFilter ----------

    @Test
    void request_withExpiredToken_shouldReturn401TokenExpired() throws Exception {
        when(jwtService.extractUsername("expired.token")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer expired.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expired"));
    }

    @Test
    void request_withMalformedToken_shouldReturn401() throws Exception {
        when(jwtService.extractUsername("garbage")).thenThrow(new MalformedJwtException("bad token"));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void request_withValidToken_shouldBeAuthenticatedByJwtFilter() throws Exception {
        User alice = buildUser(1L, "alice@test.com");
        when(jwtService.extractUsername("valid.token")).thenReturn("alice@test.com");
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(alice);
        when(jwtService.isTokenValid("valid.token", alice)).thenReturn(true);
        when(taskService.getAll(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk());

        // фильтр положил User в SecurityContext, а @AuthenticationPrincipal передал его в сервис
        verify(taskService).getAll(isNull(), isNull(), eq(0), eq(10), eq("id"), eq(alice));
    }

    // ---------- вспомогательные методы ----------

    // не называем user(...): такой метод затенил бы статический импорт SecurityMockMvcRequestPostProcessors.user
    private static User buildUser(Long id, String email) {
        User user = new User(email, "hash", Role.ROLE_USER);
        user.setId(id);
        return user;
    }

    private static TaskResponse response(Long id, String title) {
        return new TaskResponse(id, title, null, "HIGH", "NEW", null, "alice@test.com");
    }
}
