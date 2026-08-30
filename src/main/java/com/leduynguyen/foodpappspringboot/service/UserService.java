package com.leduynguyen.foodpappspringboot.service;

import com.leduynguyen.foodpappspringboot.dto.RegisterForm;
import com.leduynguyen.foodpappspringboot.dto.UpdateProfileForm;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean emailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean usernameTaken(String username) {
        return userRepository.existsByUsername(username);
    }

    public User register(RegisterForm registerForm) {
        String hashedPassword = passwordEncoder.encode(registerForm.getPassword());
        return userRepository.save(new User(registerForm.getUsername(), registerForm.getEmail(), hashedPassword));
    }

    @Transactional
    public User updateProfile(User currentUser, UpdateProfileForm updateProfileForm) {
        String newEmail = updateProfileForm.getEmail();
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(currentUser.getEmail())) {
            if (emailTaken(newEmail)) {
                throw new IllegalStateException("Email already taken");
            }
            currentUser.setEmail(newEmail);
        }

        String newUsername = updateProfileForm.getUsername();
        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(currentUser.getUsername())) {
            if (usernameTaken((newUsername))) {
                throw new IllegalStateException("Username already taken");
            }
            currentUser.setUsername(newUsername);
        }

        if (updateProfileForm.getPassword() != null && !updateProfileForm.getPassword().isBlank()) {
            currentUser.setPasswordHash(passwordEncoder.encode(updateProfileForm.getPassword()));
        }

        return userRepository.save(currentUser);
    }

    @Transactional
    public void deleteAccount(User user) {
        userRepository.delete(user);
    }

    public void addFavourite(User user, Recipe recipe) {
        user.getFavoriteRecipes().add(recipe);
        userRepository.save(user);
    }
}
