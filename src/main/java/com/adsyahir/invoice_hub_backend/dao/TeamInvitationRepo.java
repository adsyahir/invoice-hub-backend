package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.enums.InvitationStatus;
import com.adsyahir.invoice_hub_backend.model.State;
import com.adsyahir.invoice_hub_backend.model.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamInvitationRepo extends JpaRepository<TeamInvitation, Long> {

    Optional<TeamInvitation> findByTokenAndStatus(String token, InvitationStatus status);
}
