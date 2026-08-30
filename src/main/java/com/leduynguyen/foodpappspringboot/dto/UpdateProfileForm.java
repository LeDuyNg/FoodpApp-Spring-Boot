package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Edit-profile form. Unlike {@link RegisterForm} the password is optional - a
 * blank value means "keep my current password". That "blank OR &gt;= 8 chars"
 * rule can't be expressed with annotations, so it lives in
 * {@code UserService.updateProfile} instead.
 */
public class UpdateProfileForm {
    @NotBlank
    private String username;

    @NotBlank @Email(message = "Please enter a valid email address.")
    private String email;

    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
