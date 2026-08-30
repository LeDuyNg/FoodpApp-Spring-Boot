package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.service.RecipeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RecipeService recipeService;

    public HomeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(Model model) {
        Recipe pick = recipeService.recipeOfTheDay();
        model.addAttribute("recipe", pick);
        return "home";
    }

    @GetMapping("/home/following")
    public String following(Model model) {
        return "following";
    }

}
