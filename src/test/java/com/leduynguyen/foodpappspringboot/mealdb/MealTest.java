package com.leduynguyen.foodpappspringboot.mealdb;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 40-flat-field → typed conversion in {@link Meal#from}, driven by a real
 * TheMealDB payload ({@code /mealdb/papaya-salad.json}). No network.
 */
class MealTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private Meal loadPapayaSalad() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mealdb/papaya-salad.json")) {
            Map<String, Object> root = json.readValue(in, new TypeReference<>() {});
            List<Map<String, Object>> meals = (List<Map<String, Object>>) root.get("meals");
            return Meal.from(meals.get(0));
        }
    }

    @Test
    void from_pullsTheScalarFields() throws Exception {
        Meal meal = loadPapayaSalad();

        assertThat(meal.id()).isEqualTo("53573");
        assertThat(meal.title()).isEqualTo("Papaya salad");
        assertThat(meal.category()).isEqualTo("Vegetarian");
        assertThat(meal.instructions()).startsWith("Cut, peel and shred papaya.");
    }

    @Test
    void from_fallsBackToStrCountryWhenStrAreaIsNull() throws Exception {
        assertThat(loadPapayaSalad().area()).isEqualTo("Dominica");
    }

    @Test
    void from_keepsOnlyTheFilledIngredientSlots() throws Exception {
        Meal meal = loadPapayaSalad();

        assertThat(meal.ingredients()).hasSize(8);
        assertThat(meal.ingredients().get(0)).isEqualTo(new Ingredient("1/2 ", "Green Papaya"));
    }

    @Test
    void formattedIngredients_isOneTrimmedLinePerIngredient() throws Exception {
        assertThat(loadPapayaSalad().formattedIngredients()).isEqualTo(
                """
                1/2 Green Papaya
                1 tablespoon Lemon Juice
                To taste Salt
                1 Carrots
                1 cup Cherry Tomatoes
                1/2 cup Spring Onions
                1/4 cup Roasted Peanut
                1 tablespoon Garlic""");
    }

    @Test
    void description_joinsCategoryAndArea() throws Exception {
        assertThat(loadPapayaSalad().description()).isEqualTo("Vegetarian · Dominica");
    }

    @Test
    void description_fallsBackWhenNothingToCompose() {
        Meal bare = Meal.from(Map.of("idMeal", "1", "strMeal", "Mystery"));

        assertThat(bare.description()).isEqualTo("Imported from TheMealDB");
        assertThat(bare.ingredients()).isEmpty();
    }

    @Test
    void ingredientLine_dropsABlankMeasure() {
        assertThat(new Ingredient("", "Salt").line()).isEqualTo("Salt");
        assertThat(new Ingredient(null, "Pepper").line()).isEqualTo("Pepper");
        assertThat(new Ingredient(" 2 cups ", " Flour ").line()).isEqualTo("2 cups Flour");
    }
}
