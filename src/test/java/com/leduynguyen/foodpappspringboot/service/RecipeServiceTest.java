package com.leduynguyen.foodpappspringboot.service;

import com.leduynguyen.foodpappspringboot.dto.CommentForm;
import com.leduynguyen.foodpappspringboot.dto.RatingForm;
import com.leduynguyen.foodpappspringboot.dto.RecipeForm;
import com.leduynguyen.foodpappspringboot.model.Rating;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.CommentRepository;
import com.leduynguyen.foodpappspringboot.repository.RatingRepository;
import com.leduynguyen.foodpappspringboot.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for the business rules in {@link RecipeService} - no Spring
 * context, no database. The repositories are mocked, so what's under test is
 * the ownership checks, the rating upsert, and the blank-to-null search
 * normalisation.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipeRepository;
    @Mock CommentRepository commentRepository;
    @Mock RatingRepository ratingRepository;
    @InjectMocks RecipeService recipeService;

    private User owner;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        owner = new User("alice", "alice@example.com", "hash");
        owner.setId(1L);
        recipe = new Recipe("Soup", "desc", "ingredients", "instructions", owner);
    }

    private static RatingForm ratingForm(int value) {
        RatingForm f = new RatingForm();
        f.setRating(value);
        return f;
    }

    private static RecipeForm recipeForm(String title) {
        RecipeForm f = new RecipeForm();
        f.setTitle(title);
        f.setDescription("d");
        f.setIngredients("i");
        f.setInstructions("s");
        return f;
    }

    @Test
    void rate_firstTime_savesANewRatingWithTheGivenValue() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));
        when(ratingRepository.findByUserIdAndRecipeId(any(), any())).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        recipeService.rate(9L, ratingForm(4), owner);

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo(4);
    }

    @Test
    void rate_whenUserAlreadyRated_updatesTheSameRowInsteadOfInserting() {
        Rating existing = new Rating(owner, recipe, 2);
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));
        when(ratingRepository.findByUserIdAndRecipeId(any(), any())).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        recipeService.rate(9L, ratingForm(5), owner);

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getValue()).isEqualTo(5);
    }

    @Test
    void update_byNonOwner_throws() {
        User intruder = new User("mallory", "mallory@example.com", "hash");
        intruder.setId(2L);
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));

        assertThatThrownBy(() -> recipeService.update(9L, recipeForm("Hacked"), intruder))
                .isInstanceOf(IllegalStateException.class);

        verify(recipeRepository, never()).save(any());
    }

    @Test
    void update_byOwner_copiesFormFieldsAndSaves() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        recipeService.update(9L, recipeForm("New title"), owner);

        assertThat(recipe.getTitle()).isEqualTo("New title");
        verify(recipeRepository).save(recipe);
    }

    @Test
    void delete_byNonOwner_throwsAndDoesNotDelete() {
        User intruder = new User("mallory", "mallory@example.com", "hash");
        intruder.setId(2L);
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));

        assertThatThrownBy(() -> recipeService.delete(9L, intruder))
                .isInstanceOf(IllegalStateException.class);

        verify(recipeRepository, never()).delete(any(Recipe.class));
    }

    @Test
    void findById_whenMissing_throws() {
        when(recipeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.findById(404L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void search_convertsBlankAndEmptyParamsToNullBeforeQuerying() {
        recipeService.search("   ", "", null, "Hot", " ", "", "");

        verify(recipeRepository).search(isNull(), isNull(), isNull(), eq("Hot"), isNull(), isNull(), isNull());
    }

    @Test
    void addComment_persistsACommentAgainstTheRecipe() {
        CommentForm form = new CommentForm();
        form.setComment("Loved it");
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe));

        recipeService.addComment(9L, form, owner);

        verify(commentRepository).save(any());
    }
}
