package ru.zahaand.lifesync.web.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.LogoutRequestDto;
import ru.zahaand.lifesync.api.model.RefreshRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class Register {

        @Test
        @DisplayName("Should register a new user successfully")
        void shouldRegisterUser() throws Exception {
            RegisterRequestDto request = new RegisterRequestDto()
                    .email("newuser@example.com")
                    .username("new_user")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.email").value("newuser@example.com"))
                    .andExpect(jsonPath("$.username").value("new_user"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("Should return 409 for duplicate email")
        void shouldReturn409ForDuplicateEmail() throws Exception {
            RegisterRequestDto request = new RegisterRequestDto()
                    .email("dup@example.com")
                    .username("dup_user1")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            RegisterRequestDto duplicate = new RegisterRequestDto()
                    .email("dup@example.com")
                    .username("dup_user2")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicate)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 400 for invalid email")
        void shouldReturn400ForInvalidEmail() throws Exception {
            RegisterRequestDto request = new RegisterRequestDto()
                    .email("not-an-email")
                    .username("valid_user")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Login {

        @Test
        @DisplayName("Should login with email and return tokens")
        void shouldLoginWithEmail() throws Exception {
            registerUser("login_email@example.com", "login_email", "SecurePass1");

            LoginRequestDto request = new LoginRequestDto()
                    .identifier("login_email@example.com")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                    .andExpect(jsonPath("$.refreshToken").value(notNullValue()))
                    .andExpect(jsonPath("$.expiresIn").value(notNullValue()));
        }

        @Test
        @DisplayName("Should login with username")
        void shouldLoginWithUsername() throws Exception {
            registerUser("login_uname@example.com", "login_uname", "SecurePass1");

            LoginRequestDto request = new LoginRequestDto()
                    .identifier("login_uname")
                    .password("SecurePass1");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(notNullValue()));
        }

        @Test
        @DisplayName("Should return 401 for invalid credentials")
        void shouldReturn401ForInvalidCredentials() throws Exception {
            registerUser("login_fail@example.com", "login_fail", "SecurePass1");

            LoginRequestDto request = new LoginRequestDto()
                    .identifier("login_fail@example.com")
                    .password("WrongPass1");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Refresh {

        @Test
        @DisplayName("Should refresh token successfully")
        void shouldRefreshToken() throws Exception {
            registerUser("refresh@example.com", "refresh_user", "SecurePass1");
            String refreshToken = loginAndGetRefreshToken("refresh@example.com", "SecurePass1");

            RefreshRequestDto request = new RefreshRequestDto()
                    .refreshToken(refreshToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                    .andExpect(jsonPath("$.refreshToken").value(notNullValue()));
        }

        @Test
        @DisplayName("Should return 401 for revoked refresh token")
        void shouldReturn401ForRevokedToken() throws Exception {
            registerUser("refresh_revoked@example.com", "refresh_rev", "SecurePass1");
            String refreshToken = loginAndGetRefreshToken("refresh_revoked@example.com", "SecurePass1");

            // Use it once (rotation)
            RefreshRequestDto request = new RefreshRequestDto().refreshToken(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Try again with the old token — should be revoked
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Logout {

        @Test
        @DisplayName("Should logout successfully")
        void shouldLogout() throws Exception {
            registerUser("logout@example.com", "logout_user", "SecurePass1");
            String refreshToken = loginAndGetRefreshToken("logout@example.com", "SecurePass1");

            LogoutRequestDto request = new LogoutRequestDto()
                    .refreshToken(refreshToken);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            // Verify token is revoked
            RefreshRequestDto refreshRequest = new RefreshRequestDto().refreshToken(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void registerUser(String email, String username, String password) throws Exception {
        RegisterRequestDto request = new RegisterRequestDto()
                .email(email)
                .username(username)
                .password(password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetRefreshToken(String identifier, String password) throws Exception {
        LoginRequestDto request = new LoginRequestDto()
                .identifier(identifier)
                .password(password);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("refreshToken").asText();
    }
}
