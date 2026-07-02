package com.adsyahir.invoice_hub_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Postcode within a {@link City} (geo reference). Seeded by GeoSeeder. */
@Getter
@Setter
@Entity
@Table(name = "postcodes")
public class Postcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false)
    private String code;
}
