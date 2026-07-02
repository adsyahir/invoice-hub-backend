package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.validation.PasswordMatches;
import com.adsyahir.invoice_hub_backend.validation.PasswordMatching;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordMatches
public class CreateTeamMemberRequest implements PasswordMatching {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}