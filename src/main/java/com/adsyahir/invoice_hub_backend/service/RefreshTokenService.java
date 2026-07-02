package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.RefreshTokenRepo;
import com.adsyahir.invoice_hub_backend.model.RefreshToken;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RefreshTokenService {


    @Autowired
    private RefreshTokenRepo refreshTokenRepo;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepo.findByToken(token);
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepo.deleteByToken(token);
    }

    public RefreshToken addRefreshToken(User user, String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(token);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        return refreshTokenRepo.save(refreshToken);
    }
}
