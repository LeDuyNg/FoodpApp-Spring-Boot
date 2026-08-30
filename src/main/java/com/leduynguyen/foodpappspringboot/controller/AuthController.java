package com.leduynguyen.foodpappspringboot.controller;

import com.leduynguyen.foodpappspringboot.dto.RegisterForm;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "register";
        }

        if (userService.emailTaken(form.getEmail())) {
            result.rejectValue("email", "duplicate", "This email is already registered.");
            return "register";
        }

        if (userService.usernameTaken(form.getUsername())) {
            result.rejectValue("username", "duplicate", "This username is already registered.");
            return "register";
        }

        User created = userService.register(form);
        return "redirect:/login";
    }
}
