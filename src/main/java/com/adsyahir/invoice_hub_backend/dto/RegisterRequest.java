package com.adsyahir.invoice_hub_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for "Create your organization": provisions a new tenant plus its
 * first admin user in one request.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Organization name is required")
    private String orgName;

    @NotBlank(message = "Workspace subdomain is required")
    @Pattern(
            regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$",
            message = "Subdomain may only contain lowercase letters, numbers and hyphens"
    )
    private String slug;

    @NotBlank(message = "Your name is required")
    private String fullName;

    @NotBlank(message = "Work email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
