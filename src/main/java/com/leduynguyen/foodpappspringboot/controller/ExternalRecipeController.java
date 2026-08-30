package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.dto.RecipeForm;
import com.leduynguyen.foodpappspringboot.mealdb.Meal;
import com.leduynguyen.foodpappspringboot.mealdb.MealDbClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Pull a recipe from TheMealDB and let the user review it as a pre-filled
 * recipe form before saving. No class-level {@code @RequestMapping}: the paths
 * sit under {@code /recipes} alongside {@link RecipeController}, and Spring
 * matches the literal segments here ahead of {@code RecipeController}'s
 * {@code /recipes/{id}}.
 *
 * <p>There is deliberately no POST handler - the review form's {@code formAction}
 * points at the existing {@code POST /recipes} ({@link RecipeController#create}),
 * which already validates, saves, and redirects.
 */
@Controller
public class ExternalRecipeController {

    private final MealDbClient mealDbClient;

    public ExternalRecipeController(MealDbClient mealDbClient) {
        this.mealDbClient = mealDbClient;
    }

    @GetMapping("/recipes/random-from-api")
    public String random() {
        Meal meal = mealDbClient.fetchRandomMeal();
        return "redirect:/recipes/import/" + meal.id();
    }

    @GetMapping("/recipes/import/{mealId}")
    public String importForm(@PathVariable String mealId, Model model) {
        Meal meal = mealDbClient.fetchMealById(mealId);

        RecipeForm form = new RecipeForm();
        form.setTitle(meal.title());
        form.setInstructions(meal.instructions());
        form.setIngredients(meal.formattedIngredients());
        form.setDescription(meal.description());

        model.addAttribute("form", form);
        model.addAttribute("formAction", "/recipes"); // reuse RecipeController.create()
        model.addAttribute("heading", "Make this recipe yours");
        return "recipe-form";
    }
}
