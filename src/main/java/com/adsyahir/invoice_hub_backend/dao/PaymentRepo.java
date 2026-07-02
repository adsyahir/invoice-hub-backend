package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {


}
