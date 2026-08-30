package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the hand-written {@code @Query} on {@link RecipeRepository#search}
 * against a real (in-memory) database - the "{@code :param IS NULL OR ...}"
 * pattern only really proves itself when JPQL actually runs.
 */
@DataJpaTest
class RecipeRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired RecipeRepository recipeRepository;

    private Recipe hotSoup;
    private Recipe coldSalad;

    @BeforeEach
    void seed() {
        User cook = em.persist(new User("cook", "cook@example.com", "hash"));

        hotSoup = new Recipe("Tomato Soup", "cosy", "tomatoes, basil", "simmer", cook);
        hotSoup.setTemperature("Hot");
        hotSoup.setDishType("Main Course");

        coldSalad = new Recipe("Garden Salad", "fresh", "lettuce, cucumber", "toss", cook);
        coldSalad.setTemperature("Cold");
        coldSalad.setDishType("Side");

        em.persist(hotSoup);
        em.persist(coldSalad);
        em.flush();
    }

    @Test
    void search_withNoFilters_returnsEverything() {
        List<Recipe> results = recipeRepository.search(null, null, null, null, null, null, null);

        assertThat(results).extracting(Recipe::getTitle)
                .containsExactlyInAnyOrder("Tomato Soup", "Garden Salad");
    }

    @Test
    void search_byTitleFragment_matchesTitleAndIngredients() {
        assertThat(recipeRepository.search("Soup", null, null, null, null, null, null))
                .extracting(Recipe::getTitle).containsExactly("Tomato Soup");

        // "cucumber" only appears in the ingredients column
        assertThat(recipeRepository.search("cucumber", null, null, null, null, null, null))
                .extracting(Recipe::getTitle).containsExactly("Garden Salad");
    }

    @Test
    void search_byTag_filtersOnThatColumnOnly() {
        List<Recipe> hot = recipeRepository.search(null, "Hot", null, null, null, null, null);

        assertThat(hot).extracting(Recipe::getTitle).containsExactly("Tomato Soup");
    }

    @Test
    void search_combiningTitleAndTag_appliesBoth() {
        assertThat(recipeRepository.search("Salad", "Hot", null, null, null, null, null)).isEmpty();
        assertThat(recipeRepository.search("Salad", "Cold", null, null, null, null, null))
                .extracting(Recipe::getTitle).containsExactly("Garden Salad");
    }
}
