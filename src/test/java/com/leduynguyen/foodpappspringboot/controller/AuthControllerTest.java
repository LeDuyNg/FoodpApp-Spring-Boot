package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.config.SecurityConfig;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AuthController}: routing, bean validation, the
 * duplicate-email / duplicate-username branches, and CSRF enforcement. The
 * service layer is mocked.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;

    @Test
    void getRegister_rendersTheFormWithABlankBackingObject() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    void getLogin_rendersTheLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void postRegister_withInvalidInput_reRendersWithFieldErrors() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "")
                        .param("email", "not-an-email")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    void postRegister_whenEmailAlreadyRegistered_reRendersWithAnEmailError() throws Exception {
        when(userService.emailTaken("taken@example.com")).thenReturn(true);

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "freshuser")
                        .param("email", "taken@example.com")
                        .param("password", "longenoughpassword"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void postRegister_whenValidAndUnique_registersAndRedirectsToLogin() throws Exception {
        when(userService.emailTaken(any())).thenReturn(false);
        when(userService.usernameTaken(any())).thenReturn(false);
        when(userService.register(any())).thenReturn(new User("freshuser", "fresh@example.com", "hash"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "freshuser")
                        .param("email", "fresh@example.com")
                        .param("password", "longenoughpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void postRegister_withoutACsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "freshuser")
                        .param("email", "fresh@example.com")
                        .param("password", "longenoughpassword"))
                .andExpect(status().isForbidden());
    }
}
