package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.config.SecurityConfig;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.security.AppUserDetails;
import com.leduynguyen.foodpappspringboot.service.RecipeService;
import com.leduynguyen.foodpappspringboot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ProfileController}: the model each page hands its
 * template, the update form's happy / validation / duplicate paths, the
 * account-delete redirect, the login wall, and CSRF.
 */
@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean RecipeService recipeService;

    private AppUserDetails principal;

    @BeforeEach
    void setUp() {
        User user = new User("alice", "alice@example.com", "hash");
        user.setId(7L);
        principal = new AppUserDetails(user);
    }

    @Test
    void profile_rendersWithTheUsersRecipesAndFavouriteCount() throws Exception {
        when(recipeService.findByOwnerId(7L)).thenReturn(List.of());
        when(userService.favoriteCount(7L)).thenReturn(3L);

        mockMvc.perform(get("/profile").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("user", principal.getUser()))
                .andExpect(model().attributeExists("recipes"))
                .andExpect(model().attribute("favoriteCount", 3L));
    }

    @Test
    void editForm_prefillsUsernameAndEmailAndLeavesThePasswordBlank() throws Exception {
        mockMvc.perform(get("/profile/edit").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-edit"))
                .andExpect(model().attribute("form", allOf(
                        hasProperty("username", is("alice")),
                        hasProperty("email", is("alice@example.com")),
                        hasProperty("password", nullValue()))));
    }

    @Test
    void update_whenValid_appliesTheChangeThenRedirectsToLogout() throws Exception {
        mockMvc.perform(post("/profile").with(user(principal)).with(csrf())
                        .param("username", "alice2")
                        .param("email", "alice2@example.com")
                        .param("password", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logout"));

        verify(userService).updateProfile(eq(principal.getUser()), any());
    }

    @Test
    void update_withInvalidInput_reRendersTheEditForm() throws Exception {
        mockMvc.perform(post("/profile").with(user(principal)).with(csrf())
                        .param("username", "")
                        .param("email", "not-an-email")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-edit"))
                .andExpect(model().attributeHasErrors("form"));

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    void update_whenTheNewEmailIsTaken_reRendersWithAnEmailError() throws Exception {
        doThrow(new IllegalStateException("Email already taken"))
                .when(userService).updateProfile(any(), any());

        mockMvc.perform(post("/profile").with(user(principal)).with(csrf())
                        .param("username", "alice")
                        .param("email", "someone.else@example.com")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-edit"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void delete_removesTheAccountAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/profile/delete").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(userService).deleteAccount(principal.getUser());
    }

    @Test
    void favorites_rendersTheFavouritesList() throws Exception {
        when(userService.favoritesOf(7L)).thenReturn(List.of());

        mockMvc.perform(get("/profile/favorites").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("favorites"))
                .andExpect(model().attributeExists("recipes"));
    }

    @Test
    void profile_requiresLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void update_withoutACsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/profile").with(user(principal))
                        .param("username", "alice2")
                        .param("email", "alice2@example.com"))
                .andExpect(status().isForbidden());
    }
}
