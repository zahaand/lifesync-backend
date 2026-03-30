package ru.zahaand.lifesync.web.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    @Nested
    class ListUsers {

        @Test
        @DisplayName("Should list users as admin")
        void shouldListUsersAsAdmin() throws Exception {
            String adminToken = createAdminAndLogin();

            mockMvc.perform(get("/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("Should return 403 for non-admin user")
        void shouldReturn403ForNonAdmin() throws Exception {
            String userToken = registerAndLogin("nonadmin@example.com", "nonadmin_user", "SecurePass1");

            mockMvc.perform(get("/admin/users")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetUserById {

        @Test
        @DisplayName("Should return user by ID")
        void shouldReturnUserById() throws Exception {
            String adminToken = createAdminAndLogin();
            UUID userId = registerAndGetId("viewuser@example.com", "view_user", "SecurePass1");

            mockMvc.perform(get("/admin/users/" + userId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()));
        }

        @Test
        @DisplayName("Should return 404 for nonexistent user")
        void shouldReturn404ForNonexistent() throws Exception {
            String adminToken = createAdminAndLogin();

            mockMvc.perform(get("/admin/users/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class BanUser {

        @Test
        @DisplayName("Should ban user successfully")
        void shouldBanUser() throws Exception {
            String adminToken = createAdminAndLogin();
            UUID userId = registerAndGetId("banme@example.com", "ban_me", "SecurePass1");

            mockMvc.perform(post("/admin/users/" + userId + "/ban")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }

        @Test
        @DisplayName("Should return 404 when banning nonexistent user")
        void shouldReturn404WhenBanningNonexistent() throws Exception {
            String adminToken = createAdminAndLogin();

            mockMvc.perform(post("/admin/users/" + UUID.randomUUID() + "/ban")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    private String createAdminAndLogin() throws Exception {
        String email = "admin_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "admin_" + UUID.randomUUID().toString().substring(0, 8);

        registerUser(email, username, "SecurePass1");

        // Promote to ADMIN via direct DB update
        dsl.update(DSL.table("users"))
                .set(DSL.field("role", String.class), "ADMIN")
                .where(DSL.field("email", String.class).eq(email))
                .execute();

        return loginAndGetAccessToken(email, "SecurePass1");
    }

    private void registerUser(String email, String username, String password) throws Exception {
        RegisterRequestDto request = new RegisterRequestDto()
                .email(email)
                .username(username)
                .password(password);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin(String email, String username, String password) throws Exception {
        registerUser(email, username, password);
        return loginAndGetAccessToken(email, password);
    }

    private UUID registerAndGetId(String email, String username, String password) throws Exception {
        RegisterRequestDto request = new RegisterRequestDto()
                .email(email)
                .username(username)
                .password(password);

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        LoginRequestDto request = new LoginRequestDto()
                .identifier(email)
                .password(password);

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
