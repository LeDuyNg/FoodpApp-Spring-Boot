package com.leduynguyen.foodpappspringboot.repository;

import com.leduynguyen.foodpappspringboot.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
    List<Recipe> findByUserId(Long userId);

    // Dynamic search/filter for allrecipestags() (guide §8.2, Option A).
    // Each ":param IS NULL OR ..." clause means "only filter on this field
    // when the caller actually passed a value" — RecipeService.search()
    // converts blank query params to null before calling this.
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
