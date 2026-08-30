package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * CRUD for {@link Recipe}, plus "my recipes" ({@code findByUserId}) and the
 * browse-page search below.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
    List<Recipe> findByUserId(Long userId);

    // One query drives the whole browse page. Each ":param IS NULL OR ..."
    // clause is inert unless the caller passed a value, so any subset of the
    // filters can be combined. RecipeService.search() maps blank query params
    // to null before calling this.
    @Query("""
        SELECT r FROM Recipe r
        WHERE (:title IS NULL OR r.title LIKE CONCAT('%', :title, '%')
                              OR r.ingredients LIKE CONCAT('%', :title, '%'))
          AND (:temperature IS NULL OR r.temperature = :temperature)
          AND (:dishType     IS NULL OR r.dishType    = :dishType)
          AND (:dairy        IS NULL OR r.dairy       = :dairy)
          AND (:sweetness    IS NULL OR r.sweetness   = :sweetness)
          AND (:meat         IS NULL OR r.meat        = :meat)
          AND (:seafood      IS NULL OR r.seafood     = :seafood)
        """)
    List<Recipe> search(@Param("title") String title,
                        @Param("temperature") String temperature,
                        @Param("dishType") String dishType,
                        @Param("dairy") String dairy,
                        @Param("sweetness") String sweetness,
                        @Param("meat") String meat,
                        @Param("seafood") String seafood);
}
