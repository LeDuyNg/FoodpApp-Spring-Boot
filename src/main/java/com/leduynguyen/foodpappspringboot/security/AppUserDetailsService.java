package com.leduynguyen.foodpappspringboot.security;

import com.leduynguyen.foodpappspringboot.model.User;
import com.leduynguyen.foodpappspringboot.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
/**
 * How Spring Security loads an account at login time: look the {@link User} up
 * by email and wrap it in an {@link AppUserDetails}. A missing email becomes a
 * {@link UsernameNotFoundException}, which the login page reports as bad
 * credentials.
 */
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account with email " + email));
        return new AppUserDetails(user);
    }
}
