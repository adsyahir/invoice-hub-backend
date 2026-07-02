package com.adsyahir.invoice_hub_backend.dto.response;

/**
 * Internal carrier from the service to the controller: the client-facing auth
 * payload plus the raw refresh token (which the controller writes to an httpOnly
 * cookie — a web concern that stays out of the service).
 */
public record AuthResult(AuthResponse auth, String refreshToken) {
}
