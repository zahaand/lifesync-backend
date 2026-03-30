package ru.zahaand.lifesync.web.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.ConnectTelegramRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.api.model.UpdateProfileRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class GetMyProfile {

        @Test
        @DisplayName("Should return user profile")
        void shouldReturnProfile() throws Exception {
            String token = registerAndLogin("profile_view@example.com", "profile_view", "SecurePass1");

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("profile_view@example.com"))
                    .andExpect(jsonPath("$.timezone").value("UTC"));
        }

        @Test
        @DisplayName("Should return 401 without token")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class UpdateMyProfile {

        @Test
        @DisplayName("Should update profile fields")
        void shouldUpdateProfile() throws Exception {
            String token = registerAndLogin("profile_upd@example.com", "profile_upd", "SecurePass1");

            UpdateProfileRequestDto request = new UpdateProfileRequestDto()
                    .displayName("Updated Name")
                    .timezone("Europe/London");

            mockMvc.perform(patch("/api/v1/users/me")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Name"))
                    .andExpect(jsonPath("$.timezone").value("Europe/London"));
        }

        @Test
        @DisplayName("Should return 400 for invalid timezone")
        void shouldReturn400ForInvalidTimezone() throws Exception {
            String token = registerAndLogin("profile_tz@example.com", "profile_tz", "SecurePass1");

            UpdateProfileRequestDto request = new UpdateProfileRequestDto()
                    .timezone("Invalid/Zone");

            mockMvc.perform(patch("/api/v1/users/me")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class ConnectTelegram {

        @Test
        @DisplayName("Should connect Telegram account")
        void shouldConnectTelegram() throws Exception {
            String token = registerAndLogin("tg_user@example.com", "tg_user", "SecurePass1");

            ConnectTelegramRequestDto request = new ConnectTelegramRequestDto()
                    .telegramChatId("123456789");

            mockMvc.perform(put("/api/v1/users/me/telegram")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.telegramChatId").value("123456789"));
        }
    }

    @Nested
    class DeleteMyAccount {

        @Test
        @DisplayName("Should soft delete account")
        void shouldDeleteAccount() throws Exception {
            String token = registerAndLogin("del_user@example.com", "del_user", "SecurePass1");

            mockMvc.perform(delete("/api/v1/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
    }

    private String registerAndLogin(String email, String username, String password) throws Exception {
        RegisterRequestDto registerRequest = new RegisterRequestDto()
                .email(email)
                .username(username)
                .password(password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequestDto loginRequest = new LoginRequestDto()
                .identifier(email)
                .password(password);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
