package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * CRUD for {@link User} plus the lookups auth needs: by email (the login
 * identifier) and the uniqueness checks used during registration and profile
 * edits.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
