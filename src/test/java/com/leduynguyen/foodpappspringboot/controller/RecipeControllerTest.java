package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.config.SecurityConfig;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.CommentRepository;
import com.leduynguyen.foodpappspringboot.repository.RatingRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link RecipeController}: which routes are public vs
 * login-only, the create form's happy/validation paths, the delete redirect,
 * and CSRF enforcement. Every collaborator is mocked.
 */
@WebMvcTest(RecipeController.class)
@Import(SecurityConfig.class)
class RecipeControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeService recipeService;
    @MockitoBean RatingRepository ratingRepository;
    @MockitoBean CommentRepository commentRepository;
    @MockitoBean UserService userService;

    private AppUserDetails principal;

    @BeforeEach
    void setUp() {
        User user = new User("alice", "alice@example.com", "hash");
        user.setId(7L);
        principal = new AppUserDetails(user);
    }

    @Test
    void list_isPublic() throws Exception {
        when(recipeService.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/recipes"))
                .andExpect(status().isOk())
                .andExpect(view().name("recipe-list"));
    }

    @Test
    void viewSingleRecipe_isPublic() throws Exception {
        Recipe recipe = new Recipe("Soup", "d", "i", "s", new User("bob", "bob@example.com", "hash"));
        when(recipeService.findById(1L)).thenReturn(recipe);
        when(recipeService.commentsFor(1L)).thenReturn(List.of());

        mockMvc.perform(get("/recipes/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("recipe-view"))
                .andExpect(model().attributeExists("recipe"));
    }

    @Test
    void myRecipes_requiresLogin() throws Exception {
        mockMvc.perform(get("/recipes/mine"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void create_whenValid_redirectsToTheNewRecipe() throws Exception {
        Recipe saved = mock(Recipe.class);
        when(saved.getId()).thenReturn(42L);
        when(recipeService.create(any(), any())).thenReturn(saved);

        mockMvc.perform(post("/recipes").with(user(principal)).with(csrf())
                        .param("title", "Weeknight Chilli")
                        .param("description", "A big warm pot of it")
                        .param("ingredients", "beans")
                        .param("instructions", "simmer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/recipes/42"));
    }

    @Test
    void create_whenInvalid_reRendersTheFormWithItsHeadingAndAction() throws Exception {
        mockMvc.perform(post("/recipes").with(user(principal)).with(csrf())
                        .param("title", "")
                        .param("description", "")
                        .param("ingredients", "")
                        .param("instructions", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("recipe-form"))
                .andExpect(model().attributeHasErrors("form"))
                .andExpect(model().attribute("formAction", "/recipes"))
                .andExpect(model().attribute("heading", "Share a recipe"));

        verify(recipeService, never()).create(any(), any());
    }

    @Test
    void delete_redirectsToMyRecipes() throws Exception {
        mockMvc.perform(post("/recipes/5/delete").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/recipes/mine"));

        verify(recipeService).delete(5L, principal.getUser());
    }

    @Test
    void create_withoutACsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/recipes").with(user(principal))
                        .param("title", "Weeknight Chilli")
                        .param("description", "A big warm pot of it")
                        .param("ingredients", "beans")
                        .param("instructions", "simmer"))
                .andExpect(status().isForbidden());
    }
}
