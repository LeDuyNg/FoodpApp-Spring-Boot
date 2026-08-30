package com.leduynguyen.foodpappspringboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/css/**", "/images/**").permitAll()
                        // Login-only routes must be listed BEFORE the public /recipes/* rule
                        // (first match wins) so an anonymous hit lands on /login, not a
                        // controller that dereferences a null principal.
                        .requestMatchers("/recipes/mine", "/recipes/new", "/recipes/*/edit",
                                "/recipes/random-from-api", "/recipes/import/**").authenticated()
                        // Public per requirements.md use case 7: browse the list and view a
                        // single recipe. GET only — every mutating /recipes/** POST falls
                        // through to anyRequest().authenticated() below.
                        .requestMatchers(HttpMethod.GET, "/recipes", "/recipes/*").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/home", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll());
        return http.build();
    }
}
