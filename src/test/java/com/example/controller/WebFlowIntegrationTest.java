package com.example.controller;

import com.example.model.Role;
import com.example.model.Task;
import com.example.model.User;
import com.example.repository.AuditLogRepository;
import com.example.repository.TaskRepository;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий веб-интерфейса на настоящих бинах: форма логина -> сессия -> список -> создание ->
 * права (USER не удаляет, ADMIN удаляет) -> logout. Заодно доказывает, что после form-login в SecurityContext
 * лежит наш User, и @AuthenticationPrincipal в TaskWebController получает именно его.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:web-flow-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebFlowIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void formLogin_withValidCredentials_shouldAuthenticateAndRedirectToTaskList() throws Exception {
        saveUser("alice@test.com", Role.ROLE_USER);

        mockMvc.perform(formLogin("/login").user("alice@test.com").password(PASSWORD))
                .andExpect(authenticated().withUsername("alice@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));
    }

    @Test
    void formLogin_withWrongPassword_shouldRedirectBackToLoginWithError() throws Exception {
        saveUser("alice@test.com", Role.ROLE_USER);

        mockMvc.perform(formLogin("/login").user("alice@test.com").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void taskList_withoutLogin_shouldRedirectToLoginPage() throws Exception {
        mockMvc.perform(get("/web/tasks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void user_shouldCreateTaskViaForm_andSeeOnlyOwnTasks() throws Exception {
        saveUser("alice@test.com", Role.ROLE_USER);
        saveUser("bob@test.com", Role.ROLE_USER);
        MockHttpSession alice = loginAs("alice@test.com");
        MockHttpSession bob = loginAs("bob@test.com");

        createTaskViaForm(alice, "Alice task");
        createTaskViaForm(bob, "Bob task");

        mockMvc.perform(get("/web/tasks").session(alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice task")))
                .andExpect(content().string(not(containsString("Bob task"))))
                .andExpect(content().string(containsString("alice@test.com")));
        assertThat(taskRepository.findAll()).extracting(task -> task.getOwner().getEmail())
                .containsExactlyInAnyOrder("alice@test.com", "bob@test.com");
    }

    @Test
    void user_cannotDeleteViaWeb_butAdminCan() throws Exception {
        User alice = saveUser("alice@test.com", Role.ROLE_USER);
        saveUser("admin@test.com", Role.ROLE_ADMIN);
        MockHttpSession aliceSession = loginAs("alice@test.com");
        MockHttpSession adminSession = loginAs("admin@test.com");
        createTaskViaForm(aliceSession, "Alice task");
        Long taskId = onlyTaskOf(alice).getId();

        // кнопки удаления у USER нет, а прямой POST на URL отклоняется
        mockMvc.perform(get("/web/tasks").session(aliceSession))
                .andExpect(content().string(not(containsString("/web/tasks/" + taskId + "/delete"))));
        mockMvc.perform(post("/web/tasks/" + taskId + "/delete").session(aliceSession).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(taskRepository.existsById(taskId)).isTrue();

        // у ADMIN кнопка есть, удаление проходит
        mockMvc.perform(get("/web/tasks").session(adminSession))
                .andExpect(content().string(containsString("/web/tasks/" + taskId + "/delete")));
        mockMvc.perform(post("/web/tasks/" + taskId + "/delete").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));
        assertThat(taskRepository.existsById(taskId)).isFalse();
    }

    @Test
    void user_cannotOpenOrEditForeignTask() throws Exception {
        User alice = saveUser("alice@test.com", Role.ROLE_USER);
        saveUser("bob@test.com", Role.ROLE_USER);
        MockHttpSession aliceSession = loginAs("alice@test.com");
        MockHttpSession bobSession = loginAs("bob@test.com");
        createTaskViaForm(aliceSession, "Alice task");
        Long taskId = onlyTaskOf(alice).getId();

        mockMvc.perform(get("/web/tasks/" + taskId).session(bobSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/web/tasks/" + taskId + "/edit").session(bobSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/web/tasks/" + taskId).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice task")));
    }

    @Test
    void user_shouldEditOwnTaskViaForm() throws Exception {
        User alice = saveUser("alice@test.com", Role.ROLE_USER);
        MockHttpSession session = loginAs("alice@test.com");
        createTaskViaForm(session, "Old title");
        Long taskId = onlyTaskOf(alice).getId();

        mockMvc.perform(get("/web/tasks/" + taskId + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Old title\"")));
        mockMvc.perform(post("/web/tasks/" + taskId).session(session).with(csrf())
                        .param("title", "New title")
                        .param("priority", "LOW")
                        .param("status", "DONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));

        Task updated = taskRepository.findById(taskId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getStatus().name()).isEqualTo("DONE");
    }

    @Test
    void logout_shouldInvalidateSessionAndRedirectToLogin() throws Exception {
        saveUser("alice@test.com", Role.ROLE_USER);
        MockHttpSession session = loginAs("alice@test.com");

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(session.isInvalid()).isTrue();
    }

    // ---------- вспомогательные методы ----------

    private User saveUser(String email, Role role) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role));
    }

    private MockHttpSession loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/login").user(email).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void createTaskViaForm(MockHttpSession session, String title) throws Exception {
        mockMvc.perform(post("/web/tasks").session(session).with(csrf())
                        .param("title", title)
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/tasks"));
    }

    private Task onlyTaskOf(User owner) {
        List<Task> tasks = taskRepository.findAllByOwner(owner);
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }
}
