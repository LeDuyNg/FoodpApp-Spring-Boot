package com.leduynguyen.foodpappspringboot.controller;


import com.leduynguyen.foodpappspringboot.dto.CommentForm;
import com.leduynguyen.foodpappspringboot.dto.RatingForm;
import com.leduynguyen.foodpappspringboot.dto.RecipeForm;
import com.leduynguyen.foodpappspringboot.model.Rating;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.CommentRepository;
import com.leduynguyen.foodpappspringboot.repository.RatingRepository;
import com.leduynguyen.foodpappspringboot.security.AppUserDetails;
import com.leduynguyen.foodpappspringboot.service.RecipeService;
import com.leduynguyen.foodpappspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * All the recipe screens under {@code /recipes}: the public browse list and
 * single-recipe view, plus the create / edit / delete / comment / rate / favourite
 * actions for signed-in users. Every mutating handler follows Post/Redirect/Get
 * and reports outcomes with flash attributes. Ownership checks live in
 * {@code RecipeService}; this class catches the resulting
 * {@link IllegalStateException} and turns it into a flash message.
 */
@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final RatingRepository ratingRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;

    public RecipeController(RecipeService recipeService, RatingRepository ratingRepository, CommentRepository commentRepository, UserService userService) {
        this.recipeService = recipeService;
        this.ratingRepository = ratingRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String title,
                       @RequestParam(required = false) String temperature,
                       @RequestParam(required = false) String dishType,
                       @RequestParam(required = false) String dairy,
                       @RequestParam(required = false) String sweetness,
                       @RequestParam(required = false) String meat,
                       @RequestParam(required = false) String seafood,
                       Model model) {
        model.addAttribute("recipes", recipeService.search(
                title, temperature, dishType, dairy, sweetness, meat, seafood));
        model.addAttribute("title", title);
        model.addAttribute("temperature", temperature);
        model.addAttribute("dishType", dishType);
        model.addAttribute("dairy", dairy);
        model.addAttribute("seafood", seafood);
        model.addAttribute("meat", meat);
        model.addAttribute("sweetness", sweetness);
        return "recipe-list";
    }

    @GetMapping("/mine")
    public String mine(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        List<Recipe> recipes = recipeService.findByOwnerId(principal.getUser().getId());
        model.addAttribute("recipes", recipes);
        return "my-recipes";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal, Model model) {
        Recipe recipe = recipeService.findById(id);

        User currentUser = (principal != null) ? principal.getUser() : null;

        boolean isOwner = currentUser != null && recipe.getUser().getId().equals(currentUser.getId());

        boolean isFavorite = currentUser != null && userService.isFavorite(currentUser.getId(),  id);

        Integer userRating = null;
        if (currentUser != null) {
            userRating = recipeService.ratingBy(currentUser.getId(), id);
        }

        model.addAttribute("recipe", recipe);
        model.addAttribute("comments", recipeService.commentsFor(id));
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("isFavorite", isFavorite);
        model.addAttribute("userRating", userRating);
        return "recipe-view";
    }

    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @Valid @ModelAttribute("commentForm") CommentForm form,
                             BindingResult result,
                             @AuthenticationPrincipal AppUserDetails principal,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", "Your comment can't be empty.");
            return "redirect:/recipes/" + id;
        }
        try {
            recipeService.addComment(id, form, principal.getUser());
            ra.addFlashAttribute("message", "Comment posted.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/{id}/ratings")
    public String rate(@PathVariable Long id,
                       @Valid @ModelAttribute("ratingForm") RatingForm form,
                       BindingResult result,
                       @AuthenticationPrincipal AppUserDetails principal,
                       RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", "Please pick a rating between 1 and 5.");
            return "redirect:/recipes/" + id;
        }
        try {
            recipeService.rate(id, form, principal.getUser());
            ra.addFlashAttribute("message", "Thanks for rating this recipe.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new RecipeForm());
        model.addAttribute("formAction", "/recipes");
        model.addAttribute("heading", "Share a recipe");
        return "recipe-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RecipeForm form,
                         BindingResult result,
                         @AuthenticationPrincipal AppUserDetails principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("formAction", "/recipes");
            model.addAttribute("heading", "Share a recipe");
            return "recipe-form";
        }
        Recipe saved = recipeService.create(form, principal.getUser());
        ra.addFlashAttribute("message", "Recipe published.");
        return "redirect:/recipes/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal AppUserDetails principal,
                           Model model,
                           RedirectAttributes ra) {
        Recipe recipe = recipeService.findById(id);

        if (!recipe.getUser().getId().equals(principal.getUser().getId())) {
            ra.addFlashAttribute("error", "You do not have access to this recipe.");
            return "redirect:/recipes/" + id;
        }

        RecipeForm form = new RecipeForm();
        form.setTitle(recipe.getTitle());
        form.setDescription(recipe.getDescription());
        form.setIngredients(recipe.getIngredients());
        form.setInstructions(recipe.getInstructions());
        form.setTemperature(recipe.getTemperature());
        form.setDishType(recipe.getDishType());
        form.setDairy(recipe.getDairy());
        form.setSweetness(recipe.getSweetness());
        form.setMeat(recipe.getMeat());
        form.setSeafood(recipe.getSeafood());

        model.addAttribute("form", form);
        model.addAttribute("formAction", "/recipes/" + id);
        model.addAttribute("heading", "Edit your recipe");
        return "recipe-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") RecipeForm form,
                         BindingResult result,
                         @AuthenticationPrincipal AppUserDetails principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("formAction", "/recipes/" + id);
            model.addAttribute("heading", "Edit your recipe");
            return "recipe-form";
        }
        try {
            recipeService.update(id, form, principal.getUser());
            ra.addFlashAttribute("message", "Recipe updated.");
            return "redirect:/recipes/" + id;
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/recipes/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal AppUserDetails principal,
                         RedirectAttributes ra) {
        try {
            recipeService.delete(id, principal.getUser());
            ra.addFlashAttribute("message", "Recipe deleted.");
            return "redirect:/recipes/mine";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/recipes/" + id;
        }
    }

    @PostMapping("/{id}/favorite")
    public String favorite(@PathVariable Long id,
                           @AuthenticationPrincipal AppUserDetails principal,
                           RedirectAttributes ra) {
        try {
            Recipe recipe = recipeService.findById(id);
            userService.addFavorite(principal.getUser().getId(), recipe);
            ra.addFlashAttribute("message", "Saved to your favorites.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/{id}/unfavorite")
    public String unfavorite(@PathVariable Long id,
                             @AuthenticationPrincipal AppUserDetails principal,
                             RedirectAttributes ra) {
        userService.removeFavorite(principal.getUser().getId(), id);
        ra.addFlashAttribute("message", "Removed from your favorites.");
        return "redirect:/recipes/" + id;
    }

}
