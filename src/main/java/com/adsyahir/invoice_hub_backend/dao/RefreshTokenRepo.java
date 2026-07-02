package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    RefreshToken deleteByUserId(Long userId);
    void deleteByToken(String token);
}