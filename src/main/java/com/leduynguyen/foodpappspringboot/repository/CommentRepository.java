package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** CRUD for {@link Comment}, plus fetching every comment on a given recipe. */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByRecipeId(Long recipeId);
}
