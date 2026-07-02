package com.adsyahir.invoice_hub_backend.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// JSON contract is camelCase — Java fields are camelCase, so no mapping needed.
@Data
public class CreateClientRequest {

    @NotBlank(message = "Client name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;

    private String phone;

    private String taxId;

    private String addressLine1;

    @NotBlank(message = "Country is required")
    private String country;

    @NotBlank(message = "Currency is required")
    private String currency;

    // Geo is sent as foreign-key ids — numeric, so use @NotNull (not @NotBlank).
    @NotNull(message = "State is required")
    private Long state;

    @NotNull(message = "City is required")
    private Long city;

    @NotNull(message = "Postcode is required")
    private Long postcode;

    @NotNull(message = "Payment terms is required")
    private Integer paymentTermsDays;
}
