package com.example.controller;

import com.example.model.Role;
import com.example.model.User;
import com.example.repository.AuditLogRepository;
import com.example.repository.RefreshTokenRepository;
import com.example.repository.TaskRepository;
import com.example.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозные сценарии (end-to-end) на настоящих бинах и БД: ничего не замокано, весь путь
 * HTTP -> фильтр JWT -> контроллер -> сервис -> репозиторий -> H2 (схема из миграций Flyway).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth-flow-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    // ---------- Сценарий A: register -> login -> GET /tasks -> 200 ----------

    @Test
    void register_login_andGetTasks_shouldWork() throws Exception {
        JsonNode registered = register("alice@test.com");
        assertThat(registered.get("accessToken").asText()).isNotBlank();
        assertThat(registered.get("refreshToken").asText()).isNotBlank();

        JsonNode loggedIn = login("alice@test.com", PASSWORD);

        mockMvc.perform(get("/tasks").header("Authorization", bearer(loggedIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void passwordIsStoredAsBcryptHashNotPlainText() throws Exception {
        register("alice@test.com");

        String stored = userRepository.findByEmail("alice@test.com").orElseThrow().getPassword();

        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2"); // BCrypt
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
    }

    // ---------- Сценарий B: чужую задачу USER не удалит, ADMIN удалит ----------

    @Test
    void delete_foreignTask_shouldBeForbiddenForUserAndAllowedForAdmin() throws Exception {
        String ownerBearer = bearer(register("owner@test.com"));
        String strangerBearer = bearer(register("stranger@test.com"));
        saveAdmin("admin@test.com");
        String adminBearer = bearer(login("admin@test.com", PASSWORD));
        long taskId = createTask(ownerBearer, "Task of owner");

        mockMvc.perform(delete("/tasks/" + taskId).header("Authorization", strangerBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
        assertThat(taskRepository.existsById(taskId)).isTrue();

        mockMvc.perform(delete("/tasks/" + taskId).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());
        assertThat(taskRepository.existsById(taskId)).isFalse();
    }

    @Test
    void delete_ownTask_shouldBeAllowedForOwner() throws Exception {
        String ownerBearer = bearer(register("owner@test.com"));
        long taskId = createTask(ownerBearer, "My own task");

        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", ownerBearer))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.existsById(taskId)).isFalse();
    }

    @Test
    void getForeignTask_shouldBeForbiddenForUser() throws Exception {
        String ownerBearer = bearer(register("owner@test.com"));
        String strangerBearer = bearer(register("stranger@test.com"));
        long taskId = createTask(ownerBearer, "Private task");

        mockMvc.perform(get("/tasks/" + taskId).header("Authorization", strangerBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/tasks/" + taskId).header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerEmail").value("owner@test.com"));
    }

    // ---------- Сценарий C: дубликат email -> 409 ----------

    @Test
    void register_withExistingEmail_shouldReturn409() throws Exception {
        register("alice@test.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ALICE@test.com", PASSWORD))) // регистр email не важен
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    // ---------- Сценарий D: /tasks/my и изоляция данных ----------

    @Test
    void tasksMy_shouldReturnOnlyTasksOfCurrentUser() throws Exception {
        String aliceBearer = bearer(register("alice@test.com"));
        String bobBearer = bearer(register("bob@test.com"));
        createTask(aliceBearer, "Alice task");
        createTask(bobBearer, "Bob task");
        saveAdmin("admin@test.com");
        String adminBearer = bearer(login("admin@test.com", PASSWORD));

        mockMvc.perform(get("/tasks/my").header("Authorization", aliceBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Alice task"))
                .andExpect(jsonPath("$[0].ownerEmail").value("alice@test.com"));

        // обычный пользователь и в общем списке видит только своё, ADMIN - всё
        mockMvc.perform(get("/tasks").header("Authorization", aliceBearer))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/tasks").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/tasks/stats").header("Authorization", aliceBearer))
                .andExpect(jsonPath("$.NEW").value(1));
    }

    // ---------- Сценарий E: refresh-токены ----------

    @Test
    void refresh_shouldRotateTokens_andOldRefreshTokenStopsWorking() throws Exception {
        JsonNode first = register("alice@test.com");
        String oldRefresh = first.get("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = body(refreshed);

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh); // ротация
        mockMvc.perform(get("/tasks").header("Authorization", bearer(second))).andExpect(status().isOk());
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void logout_shouldRevokeRefreshTokens() throws Exception {
        JsonNode tokens = register("alice@test.com");

        mockMvc.perform(post("/auth/logout").header("Authorization", bearer(tokens)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        assertThat(refreshTokenRepository.count()).isZero();
    }

    // ---------- Сценарий F: плохие запросы ----------

    @Test
    void protectedEndpoint_withoutOrWithBrokenToken_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        register("alice@test.com");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice@test.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void openApiDocs_shouldBePublicAndDescribeBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Task Manager API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    // ---------- вспомогательные методы ----------

    private JsonNode register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private long createTask(String bearerToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    private void saveAdmin(String email) {
        userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), Role.ROLE_ADMIN));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
