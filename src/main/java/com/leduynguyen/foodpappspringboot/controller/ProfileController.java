package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.dto.UpdateProfileForm;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.security.AppUserDetails;
import com.leduynguyen.foodpappspringboot.service.RecipeService;
import com.leduynguyen.foodpappspringboot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final RecipeService recipeService;


    public ProfileController(UserService userService,  RecipeService recipeService) {
        this.userService = userService;
        this.recipeService = recipeService;
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        List<Recipe> recipes = recipeService.findByOwnerId(user.getId());
        Long favoriteCount = userService.favoriteCount(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("recipes", recipes);
        model.addAttribute("favoriteCount", favoriteCount);
        return "profile";
    }

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        UpdateProfileForm updateProfileForm = new UpdateProfileForm();
        updateProfileForm.setEmail(user.getEmail());
        updateProfileForm.setUsername(user.getUsername());
        model.addAttribute("form", updateProfileForm);
        return "profile-edit";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("form") UpdateProfileForm form,
                         BindingResult result,
                         @AuthenticationPrincipal AppUserDetails principal) {
        if (result.hasErrors()) {
            return "profile-edit";
        }

        try {
            userService.updateProfile(principal.getUser(), form);
        }
        catch (IllegalStateException e) {
            String errMsg = e.getMessage();
            if (errMsg.contains("Email")) {
                result.rejectValue("email", "duplicate", errMsg);
            } else {
                result.rejectValue("username", "duplicate", errMsg);
            }
            return "profile-edit";
        }
        // Success: the logged-in principal now holds a stale username/email, so
        // send the user through logout to re-authenticate with the new details.
        return "redirect:/logout";
    }

    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         HttpServletRequest request) {
        userService.deleteAccount(principal.getUser());
        request.getSession().invalidate();
        return "redirect:/login";
    }

    @GetMapping("/favorites")
    public String favorites(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = principal.getUser();
        List<Recipe> recipes = userService.favoritesOf(user.getId());
        model.addAttribute("recipes", recipes);
        return "favorites";
    }
}
