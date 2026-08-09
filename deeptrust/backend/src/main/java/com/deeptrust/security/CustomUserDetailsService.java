package com.deeptrust.security;

import com.deeptrust.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(CurrentUser::new)
                // Deliberately generic message — do not reveal whether the
                // username exists to avoid user-enumeration via error text.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
