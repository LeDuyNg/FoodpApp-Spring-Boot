package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * CRUD for {@link Rating}. {@code findByUserIdAndRecipeId} powers the
 * "one rating per user per recipe" upsert in {@code RecipeService.rate}.
 */
public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByUserIdAndRecipeId(Long userId, Long recipeId);
}
