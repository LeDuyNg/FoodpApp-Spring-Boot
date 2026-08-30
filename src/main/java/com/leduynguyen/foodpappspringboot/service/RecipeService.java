package com.leduynguyen.foodpappspringboot.service;

import com.leduynguyen.foodpappspringboot.dto.CommentForm;
import com.leduynguyen.foodpappspringboot.dto.RatingForm;
import com.leduynguyen.foodpappspringboot.dto.RecipeForm;
import com.leduynguyen.foodpappspringboot.model.Comment;
import com.leduynguyen.foodpappspringboot.model.Rating;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.CommentRepository;
import com.leduynguyen.foodpappspringboot.repository.RatingRepository;
import com.leduynguyen.foodpappspringboot.repository.RecipeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for recipes, comments and ratings. Ownership is enforced here,
 * not in the controller: {@link #update} and {@link #delete} throw
 * {@link IllegalStateException} if the caller isn't the recipe's author.
 * Read methods run in read-only transactions so their lazy associations stay
 * loadable while a view renders.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final RatingRepository ratingRepository;

    public RecipeService(RecipeRepository recipeRepository,
                         CommentRepository commentRepository,
                         RatingRepository ratingRepository) {
        this.recipeRepository = recipeRepository;
        this.commentRepository = commentRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public Recipe create(RecipeForm form, User owner) {
        String title = form.getTitle();
        String description = form.getDescription();
        String ingredients = form.getIngredients();
        String instructions = form.getInstructions();
        Recipe recipe = new Recipe(title, description, ingredients, instructions, owner);
        recipe.setDairy(form.getDairy());
        recipe.setDishType(form.getDishType());
        recipe.setMeat(form.getMeat());
        recipe.setSeafood(form.getSeafood());
        recipe.setSweetness(form.getSweetness());
        recipe.setTemperature(form.getTemperature());
        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe update(Long recipeId, RecipeForm form, User currentUser) {
        Recipe recipe = findById(recipeId);

        if (!recipe.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Current user is not the owner of the recipe.");
        }
        recipe.setTitle(form.getTitle());
        recipe.setDescription(form.getDescription());
        recipe.setIngredients(form.getIngredients());
        recipe.setInstructions(form.getInstructions());
        recipe.setDairy(form.getDairy());
        recipe.setDishType(form.getDishType());
        recipe.setMeat(form.getMeat());
        recipe.setSeafood(form.getSeafood());
        recipe.setTemperature(form.getTemperature());
        recipe.setSweetness(form.getSweetness());
        return recipeRepository.save(recipe);
    }

    @Transactional
    public void delete(Long recipeId, User currentUser) {
        Recipe recipe = findById(recipeId);

        if (!recipe.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Current user is not the owner of the recipe.");
        }
        recipeRepository.delete(recipe);
    }

    @Transactional
    public Comment addComment(Long recipeId, CommentForm form, User author) {
        Recipe recipe = findById(recipeId);
        return commentRepository.save(new Comment(author, recipe, form.getComment()));
    }

    /** Upsert: reuse this user's existing rating row for the recipe, or start a new one. */
    @Transactional
    public Rating rate(Long recipeId, RatingForm form, User rater) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalStateException("Recipe not found"));
        Rating rating = ratingRepository.findByUserIdAndRecipeId(rater.getId(), recipeId)
                .orElseGet(() -> new Rating(rater, recipe, form.getRating()));
        rating.setValue(form.getRating());
        return ratingRepository.save(rating);
    }

    public List<Recipe> search(String title, String temperature, String dishType, String dairy,
                               String sweetness, String meat, String seafood) {
        return recipeRepository.search(
                blankToNull(title), blankToNull(temperature), blankToNull(dishType),
                blankToNull(dairy), blankToNull(sweetness), blankToNull(meat), blankToNull(seafood)
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * A recipe picked pseudo-randomly but stably for the whole calendar day
     * (the RNG is seeded with today's date), so the home page shows the same
     * "recipe of the day" on every visit. {@code null} when there are none yet.
     */
    @Transactional(readOnly = true)
    public Recipe recipeOfTheDay() {
        List<Recipe> all = recipeRepository.findAll(Sort.by("id"));
        if (all.isEmpty()) {
            return null;
        }
        long seed = java.time.LocalDate.now().toEpochDay();
        java.util.Random random = new java.util.Random(seed);
        return all.get(random.nextInt(all.size()));
    }

    @Transactional(readOnly = true)
    public Recipe findById(Long recipeId) {
        return recipeRepository.findById(recipeId).orElseThrow(() -> new IllegalStateException("Recipe not found"));
    }

    @Transactional(readOnly = true)
    public List<Recipe> findByOwnerId(Long ownerId) {
        return recipeRepository.findByUserId(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Comment> commentsFor(Long recipeId) {
        return commentRepository.findByRecipeId(recipeId);
    }

    @Transactional(readOnly = true)
    public Integer ratingBy(Long userId, Long recipeId) {
        return ratingRepository.findByUserIdAndRecipeId(userId, recipeId)
                .map(Rating::getValue).orElse(null);
    }
}
