package com.adsyahir.invoice_hub_backend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API view of a Client. Flattens geo to names and omits the tenant + JPA
 * proxy internals so we never leak the entity graph to the frontend.
 */
@Getter
@Builder
public class ClientResponse {
    private Long id;        // internal bigint — used for row actions like delete
    private UUID uuid;      // public handle — used in URLs (view/edit)
    private String name;
    private String email;
    private String phone;
    private String taxId;
    private String addressLine1;
    // Geo: ids drive the edit-form dropdowns, names/code are for display.
    private Long stateId;
    private String state;      // state name
    private Long cityId;
    private String city;       // city name
    private Long postcodeId;
    private String postcode;   // postcode code
    private String country;
    private String currency;
    private Integer paymentTermsDays;
    private LocalDateTime createdAt;
}
