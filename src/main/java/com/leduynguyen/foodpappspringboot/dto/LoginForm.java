package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Backs the sign-in form. Spring Security's form-login filter actually reads the
 * raw {@code email}/{@code password} request parameters itself, so this class is
 * only used where a bound object is convenient (e.g. re-showing the page).
 */
public class LoginForm {
    @NotBlank @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank
    private String password;

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
