package com.leduynguyen.foodpappspringboot.mealdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A strongly-typed view of one meal from TheMealDB.
 *
 * <p>The API returns ingredients as 40 flat fields
 * ({@code strIngredient1..20} / {@code strMeasure1..20}); {@link #from(Map)} is
 * the single place that flattening lives, so the rest of the app sees a tidy
 * {@code List<Ingredient>}.
 */
public record Meal(
        String id,
        String title,
        String category,
        String area,
        String instructions,
        List<Ingredient> ingredients
) {

    /**
     * Something non-blank for {@code RecipeForm.description} (which is
     * {@code @NotBlank}) - TheMealDB has no description field of its own.
     */
    public String description() {
        String composed = Stream.of(category, area)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
        return composed.isBlank() ? "Imported from TheMealDB" : composed;
    }

    /** One {@code "measure name"} per line - the shape the recipe form expects. */
    public String formattedIngredients() {
        return ingredients.stream().map(Ingredient::line).collect(Collectors.joining("\n"));
    }

    /** Build a {@code Meal} from one entry of TheMealDB's {@code "meals"} array. */
    public static Meal from(Map<String, Object> raw) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String name = str(raw, "strIngredient" + i);
            if (name == null || name.isBlank()) {
                continue; // real meals stop well before 20, and gaps happen
            }
            ingredients.add(new Ingredient(str(raw, "strMeasure" + i), name));
        }

        String area = str(raw, "strArea");
        if (area == null || area.isBlank()) {
            area = str(raw, "strCountry"); // some rows carry strCountry instead
        }

        return new Meal(
                str(raw, "idMeal"),
                str(raw, "strMeal"),
                str(raw, "strCategory"),
                area,
                str(raw, "strInstructions"),
                List.copyOf(ingredients)
        );
    }

    private static String str(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : value.toString();
    }
}
