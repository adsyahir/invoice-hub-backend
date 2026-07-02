package com.adsyahir.invoice_hub_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Malaysian state (geo reference). Seeded by GeoSeeder. */
@Getter
@Setter
@Entity
@Table(name = "states")
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}
