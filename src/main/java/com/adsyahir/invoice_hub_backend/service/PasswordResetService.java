package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.PasswordResetTokenRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.model.PasswordResetToken;
import com.adsyahir.invoice_hub_backend.model.User;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Forgot / reset password flow (US-005). "Forgot" issues a single-use,
 * time-limited token and emails a reset link; "reset" validates the token and
 * sets a new BCrypt-hashed password. Both endpoints are deliberately silent
 * about whether an email exists, to avoid user enumeration.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_TTL_MINUTES = 60;

    private final UserRepo userRepo;
    private final PasswordResetTokenRepo tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public PasswordResetService(UserRepo userRepo, PasswordResetTokenRepo tokenRepo,
                                PasswordEncoder passwordEncoder, JavaMailSender mailSender) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    /** Issue a reset token and email the link. No-op (silently) if no such user. */
    @Transactional
    public void requestReset(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            log.info("Password reset requested for unknown email — ignoring");
            return;
        }

        String token = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "");
        PasswordResetToken entry = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES))
                .build();
        tokenRepo.save(entry);

        emailResetLink(user, token);
    }

    /** Validate the token and set the new password. Throws 400 if invalid/expired. */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken entry = tokenRepo.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired"));

        if (!entry.isUsable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This reset link is invalid or has expired");
        }

        User user = entry.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        entry.setUsedAt(LocalDateTime.now());
        tokenRepo.save(entry);
        log.info("Password reset completed for user {}", user.getEmail());
    }

    /** Best-effort delivery of the reset link. Never throws. */
    private void emailResetLink(User user, String token) {
        String link = appBaseUrl + "/reset-password?token=" + token;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom("no-reply@invoicehub.local");
            helper.setTo(user.getEmail());
            helper.setSubject("Reset your InvoiceHub password");
            helper.setText("""
                    Hello %s,

                    We received a request to reset your InvoiceHub password. Use the link
                    below within the next %d minutes to choose a new one:

                    %s

                    If you didn't request this, you can safely ignore this email.
                    """.formatted(user.getFullName(), TOKEN_TTL_MINUTES, link), false);
            mailSender.send(message);
            log.info("Sent password reset link to {}", user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
