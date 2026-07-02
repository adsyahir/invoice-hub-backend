package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StateRepo extends JpaRepository<State, Long> {

    List<State> findAll();  // no @Query needed
}
