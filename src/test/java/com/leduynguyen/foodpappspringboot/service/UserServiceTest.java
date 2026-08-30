package com.leduynguyen.foodpappspringboot.service;

import com.leduynguyen.foodpappspringboot.dto.RegisterForm;
import com.leduynguyen.foodpappspringboot.dto.UpdateProfileForm;
import com.leduynguyen.foodpappspringboot.model.Recipe;
import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService} - password hashing on register, and the
 * "only touch a field the form actually changed / don't collide with someone
 * else" rules in updateProfile.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    void register_storesTheHashedPasswordNeverThePlaintext() {
        RegisterForm form = new RegisterForm();
        form.setUsername("bob");
        form.setEmail("bob@example.com");
        form.setPassword("supersecret");
        when(passwordEncoder.encode("supersecret")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register(form);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("ENCODED");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("supersecret");
    }

    @Test
    void updateProfile_whenNewEmailBelongsToSomeoneElse_throws() {
        User current = new User("me", "me@example.com", "hash");
        UpdateProfileForm form = new UpdateProfileForm();
        form.setUsername("me");
        form.setEmail("taken@example.com");
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(current, form))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_withBlankPassword_leavesTheExistingHashAlone() {
        User current = new User("me", "me@example.com", "originalHash");
        UpdateProfileForm form = new UpdateProfileForm();
        form.setUsername("me");
        form.setEmail("me@example.com");
        form.setPassword("");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(current, form);

        assertThat(current.getPasswordHash()).isEqualTo("originalHash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateProfile_withANewUsername_checksItIsFreeThenApplies() {
        User current = new User("me", "me@example.com", "hash");
        UpdateProfileForm form = new UpdateProfileForm();
        form.setUsername("brandNew");
        form.setEmail("me@example.com");
        when(userRepository.existsByUsername("brandNew")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(current, form);

        assertThat(current.getUsername()).isEqualTo("brandNew");
        verify(userRepository).existsByUsername("brandNew");
    }

    @Test
    void isFavorite_isTrueWhenTheRecipeIsInTheUsersFavourites() {
        Recipe favourited = mock(Recipe.class);
        when(favourited.getId()).thenReturn(5L);
        User user = new User("me", "me@example.com", "hash");
        user.getFavoriteRecipes().add(favourited);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.isFavorite(1L, 5L)).isTrue();
        assertThat(userService.isFavorite(1L, 999L)).isFalse();
    }

    @Test
    void isFavorite_isFalseWhenTheUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(userService.isFavorite(1L, 5L)).isFalse();
    }
}
