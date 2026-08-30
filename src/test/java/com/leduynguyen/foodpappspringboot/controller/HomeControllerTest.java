package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.config.SecurityConfig;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.security.AppUserDetails;
import com.leduynguyen.foodpappspringboot.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link HomeController}: the "/" redirect, the recipe-of-the-day
 * model attribute, and that "/home" is behind the login wall.
 */
@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeService recipeService;

    @Test
    void root_redirectsToHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    void home_whenLoggedIn_rendersHomeWithTheRecipeOfTheDay() throws Exception {
        Recipe pick = new Recipe("Stew", "d", "i", "s", new User("cook", "cook@example.com", "hash"));
        when(recipeService.recipeOfTheDay()).thenReturn(pick);

        // The navbar fragment reads sec:authentication="principal.user.username",
        // so the authenticated principal has to be our AppUserDetails, not a
        // bare username.
        User me = new User("me", "me@example.com", "hash");
        me.setId(1L);

        mockMvc.perform(get("/home").with(user(new AppUserDetails(me))))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("recipeOfTheDay", pick));
    }

    @Test
    void home_whenAnonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
