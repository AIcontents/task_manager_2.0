package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskNotFoundException;
import com.example.model.Priority;
import com.example.security.JwtService;
import com.example.security.SecurityExceptionHandler;
import com.example.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Веб-интерфейс на Thymeleaf: страницы рендерятся по-настоящему (шаблоны из src/main/resources/templates),
 * сервис замокан. Проверяем и контроллер, и шаблоны, и правила безопасности (форма логина, роли, CSRF).
 */
@WebMvcTest({TaskWebController.class, AuthWebController.class})
@Import({SecurityConfig.class, SecurityExceptionHandler.class})
class TaskWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ---------- анонимный пользователь ----------

    @Test
    void list_whenAnonymous_shouldRedirectToLoginPage() throws Exception {
        mockMvc.perform(get("/web/tasks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(taskService);
    }

    @Test
    void restPath_whenAnonymous_shouldStillReturnJson401InsteadOfRedirect() throws Exception {
        // две цепочки безопасности: REST остаётся на JWT (401 JSON), веб - на форме логина (302)
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginPage_shouldBePublicAndContainForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPage_withErrorParam_shouldShowErrorMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Неверный email или пароль")));
    }

    @Test
    void loginPage_withLogoutParam_shouldShowLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы вышли из системы")));
    }

    // ---------- список: кнопки зависят от роли ----------

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void list_asUser_shouldShowTasksAndEditLinkButNoDeleteButton() throws Exception {
        when(taskService.getAll(any())).thenReturn(List.of(response(1L, "Buy milk")));

        mockMvc.perform(get("/web/tasks"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/list"))
                .andExpect(model().attributeExists("tasks"))
                .andExpect(content().string(containsString("Buy milk")))
                .andExpect(content().string(containsString("user@test.com")))       // имя в навбаре
                .andExpect(content().string(containsString("/web/tasks/1/edit")))   // USER может редактировать
                .andExpect(content().string(not(containsString("/web/tasks/1/delete")))); // удалять - нет
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void list_asAdmin_shouldShowDeleteForm() throws Exception {
        when(taskService.getAll(any())).thenReturn(List.of(response(1L, "Buy milk")));

        mockMvc.perform(get("/web/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/web/tasks/1/delete")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));      // CSRF-поле в POST-форме
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_whenNoTasks_shouldShowEmptyMessage() throws Exception {
        when(taskService.getAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/web/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Задач пока нет")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void root_shouldRedirectToTaskList() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));
    }

    // ---------- детали ----------

    @Test
    @WithMockUser(roles = "USER")
    void detail_shouldRenderTask() throws Exception {
        when(taskService.getOrThrow(eq(1L), any())).thenReturn(response(1L, "Buy milk"));

        mockMvc.perform(get("/web/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/detail"))
                .andExpect(content().string(containsString("Buy milk")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void detail_whenTaskNotFound_shouldReturn404() throws Exception {
        when(taskService.getOrThrow(eq(999L), any())).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/web/tasks/999")).andExpect(status().isNotFound());
    }

    // ---------- создание: валидация, redirect после POST, CSRF ----------

    @Test
    @WithMockUser(roles = "USER")
    void newForm_shouldRenderEmptyFormWithCsrfField() throws Exception {
        mockMvc.perform(get("/web/tasks/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attributeExists("taskRequest"))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("action=\"/web/tasks\"")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_withoutCsrfToken_shouldBeForbidden() throws Exception {
        // для form-login CSRF включён: POST без токена отклоняется
        mockMvc.perform(post("/web/tasks").param("title", "Buy milk"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_withBlankTitle_shouldRedisplayFormWithValidationError() throws Exception {
        mockMvc.perform(post("/web/tasks").with(csrf()).param("title", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attributeHasFieldErrors("taskRequest", "title"))
                .andExpect(content().string(containsString("Название задачи не может быть пустым")));

        verifyNoInteractions(taskService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_whenValid_shouldCallServiceAndRedirect() throws Exception {
        mockMvc.perform(post("/web/tasks").with(csrf())
                        .param("title", "Buy milk")
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks")); // Post-Redirect-Get: F5 не создаст дубликат

        verify(taskService).create(
                argThat((TaskRequest request) -> "Buy milk".equals(request.getTitle())
                        && request.getPriority() == Priority.HIGH),
                any());
    }

    // ---------- редактирование ----------

    @Test
    @WithMockUser(roles = "USER")
    void editForm_shouldPrefillFieldsAndPointActionToTaskId() throws Exception {
        when(taskService.getOrThrow(eq(1L), any())).thenReturn(response(1L, "Buy milk"));

        mockMvc.perform(get("/web/tasks/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attribute("taskId", 1L))
                .andExpect(content().string(containsString("value=\"Buy milk\"")))
                .andExpect(content().string(containsString("action=\"/web/tasks/1\"")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void update_whenValid_shouldCallServiceAndRedirect() throws Exception {
        when(taskService.update(eq(1L), any(TaskRequest.class), any()))
                .thenReturn(Optional.of(response(1L, "New title")));

        mockMvc.perform(post("/web/tasks/1").with(csrf()).param("title", "New title"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void update_withBlankTitle_shouldRedisplayFormKeepingTaskId() throws Exception {
        mockMvc.perform(post("/web/tasks/1").with(csrf()).param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attribute("taskId", 1L))
                .andExpect(content().string(containsString("action=\"/web/tasks/1\"")));
    }

    // ---------- удаление: только ADMIN ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_shouldCallServiceAndRedirect() throws Exception {
        mockMvc.perform(post("/web/tasks/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));

        verify(taskService).delete(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_asUser_shouldBeForbidden() throws Exception {
        mockMvc.perform(post("/web/tasks/1/delete").with(csrf()))
                .andExpect(status().isForbidden());

        verify(taskService, never()).delete(any(), any());
    }

    // ---------- вспомогательные методы ----------

    private static TaskResponse response(Long id, String title) {
        return new TaskResponse(id, title, "Описание", "HIGH", "NEW", null, "owner@test.com");
    }
}
