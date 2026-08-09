package com.deeptrust.auth.service;

import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.auth.dto.AuthDtos.AuthResponse;
import com.deeptrust.security.CurrentUser;
import com.deeptrust.security.jwt.JwtService;
import com.deeptrust.user.User;
import com.deeptrust.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    @Transactional
    public AuthResponse login(String username, String password, String clientIp) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException ex) {
            // Generic failure logged without leaking whether it was the
            // username or password that was wrong (anti-enumeration).
            auditLogService.logSecurityEvent("LOGIN_FAILED", clientIp, "username=" + username);
            throw ex;
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        CurrentUser principal = new CurrentUser(user);
        String accessToken = jwtService.generateAccessToken(principal, user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(principal, user.getId());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditLogService.logAction(user.getId(), "LOGIN_SUCCESS", "User", user.getId(), "ip=" + clientIp);

        return new AuthResponse(accessToken, refreshToken, user.getRole().name(), user.isMustChangePassword());
    }

    public AuthResponse refresh(String refreshToken) {
        if (!"refresh".equals(jwtService.extractTokenType(refreshToken))) {
            throw new BadCredentialsException("Not a refresh token");
        }
        String username = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        CurrentUser principal = new CurrentUser(user);
        if (!jwtService.isTokenValid(refreshToken, principal)) {
            throw new BadCredentialsException("Expired or invalid refresh token");
        }

        String newAccessToken = jwtService.generateAccessToken(principal, user.getId(), user.getRole().name());
        // Rotate the refresh token too (defense against replay of a stolen one).
        String newRefreshToken = jwtService.generateRefreshToken(principal, user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken, user.getRole().name(), user.isMustChangePassword());
    }
}
