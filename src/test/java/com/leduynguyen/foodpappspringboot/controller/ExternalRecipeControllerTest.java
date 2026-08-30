package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.config.SecurityConfig;
import com.leduynguyen.foodpappspringboot.mealdb.Ingredient;
import com.leduynguyen.foodpappspringboot.mealdb.Meal;
import com.leduynguyen.foodpappspringboot.mealdb.MealDbClient;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ExternalRecipeController}: the random → review-form
 * redirect, the pre-filled form the review screen hands the template, and the
 * login wall.
 */
@WebMvcTest(ExternalRecipeController.class)
@Import(SecurityConfig.class)
class ExternalRecipeControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean MealDbClient mealDbClient;

    private AppUserDetails principal;

    @BeforeEach
    void setUp() {
        User user = new User("alice", "alice@example.com", "hash");
        user.setId(7L);
        principal = new AppUserDetails(user);
    }

    private static Meal papayaSalad() {
        return new Meal("53573", "Papaya salad", "Vegetarian", "Dominica",
                "Cut, peel and shred papaya.",
                List.of(new Ingredient("1/2", "Green Papaya"), new Ingredient("1 cup", "Cherry Tomatoes")));
    }

    @Test
    void random_redirectsToTheReviewFormForThatMeal() throws Exception {
        when(mealDbClient.fetchRandomMeal()).thenReturn(papayaSalad());

        mockMvc.perform(get("/recipes/random-from-api").with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/recipes/import/53573"));
    }

    @Test
    void importForm_prefillsTheRecipeFormFromTheMeal() throws Exception {
        when(mealDbClient.fetchMealById("53573")).thenReturn(papayaSalad());

        mockMvc.perform(get("/recipes/import/53573").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("recipe-form"))
                .andExpect(model().attribute("formAction", "/recipes"))
                .andExpect(model().attribute("heading", "Make this recipe yours"))
                .andExpect(model().attribute("form", allOf(
                        hasProperty("title", is("Papaya salad")),
                        hasProperty("description", is("Vegetarian · Dominica")),
                        hasProperty("ingredients", is("1/2 Green Papaya\n1 cup Cherry Tomatoes")),
                        hasProperty("instructions", is("Cut, peel and shred papaya.")))));
    }

    @Test
    void random_requiresLogin() throws Exception {
        mockMvc.perform(get("/recipes/random-from-api"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
