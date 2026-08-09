package com.deeptrust.bootstrap;

import com.deeptrust.user.Role;
import com.deeptrust.user.User;
import com.deeptrust.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates the single founder ROOT account ("Ranjitha") on first startup.
 *
 * Security properties:
 *  - Runs only if NO user with Role.ROOT already exists (idempotent, safe to
 *    leave in production code — it will never overwrite or duplicate ROOT).
 *  - Credentials come exclusively from environment variables; nothing is
 *    ever hardcoded in source, so this file is safe to commit.
 *  - The generated account is forced into mustChangePassword=true, so the
 *    bootstrap password is single-use by design.
 *  - If the required env vars are absent, bootstrap is skipped with a clear
 *    log warning rather than failing startup — useful for CI/test profiles
 *    that don't need a ROOT user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RootUserBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${deeptrust.root.username:#{null}}")
    private String rootUsername;

    @Value("${deeptrust.root.email:#{null}}")
    private String rootEmail;

    @Value("${deeptrust.root.initial-password:#{null}}")
    private String rootInitialPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ROOT)) {
            log.info("ROOT account already provisioned — skipping bootstrap.");
            return;
        }

        if (rootUsername == null || rootEmail == null || rootInitialPassword == null) {
            log.warn("ROOT bootstrap skipped: DEEPTRUST_ROOT_USERNAME / " +
                    "DEEPTRUST_ROOT_EMAIL / DEEPTRUST_ROOT_INITIAL_PASSWORD not set.");
            return;
        }

        User root = User.builder()
                .username(rootUsername)
                .email(rootEmail)
                .passwordHash(passwordEncoder.encode(rootInitialPassword))
                .fullName("Ranjitha (Founder)")
                .role(Role.ROOT)
                .enabled(true)
                .accountNonLocked(true)
                .mustChangePassword(true) // forces rotation on first login
                .createdAt(Instant.now())
                .build();

        userRepository.save(root);
        log.info("ROOT account bootstrapped for username='{}'. Password rotation required on first login.", rootUsername);
    }
}
