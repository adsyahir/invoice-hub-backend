package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteTeamMemberRequest {

    @Email(message = "Email is invalid")
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "Role is required")
    private String role;
}
