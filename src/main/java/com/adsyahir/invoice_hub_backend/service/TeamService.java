package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.TeamInvitationRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.response.TeamMemberResponse;
import com.adsyahir.invoice_hub_backend.enums.InvitationStatus;
import com.adsyahir.invoice_hub_backend.enums.RoleName;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.TeamInvitation;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    @Autowired
    private UserRepo userRepo;
//
    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private TeamInvitationRepo teamInvitationRepo;

    private final JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    private static final int INVITE_EXPIRY_HOURS = 48;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);


    public TeamService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Value("${app.base-url}")
    private String appBaseUrl;
    /**
     * Remove a team member from the caller's tenant. Soft-deletes (sets deleted_at)
     * rather than hard-deleting, so invoices/payments the member created stay intact.
     * Scoped to the caller's tenant so one tenant can't remove another's users.
     */
    @Transactional
    public void delete(UUID uuid, Long tenantId, Long currentUserId) {
        System.out.println(uuid);
        System.out.println(tenantId);
        System.out.println(currentUserId);

        User member = userRepo.findByUuidAndTenantIdAndDeletedAtIsNull(uuid, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found"));

        if (member.getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove yourself");
        }

        member.setDeletedAt(LocalDateTime.now());
        member.setStatus("SUSPENDED");
        System.out.println("Hehe");
        userRepo.save(member);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> teamList(Long tenantId){
        return userRepo.findAllByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(this::toTeamMember)
                .toList();
    }

    private TeamMemberResponse toTeamMember(User u) {
        return TeamMemberResponse.builder()
                .uuid(u.getUuid())              // public handle (add uuid to User if not there)
                .fullName(u.getFullName())
                .email(u.getEmail())
                .role(u.getRole().getName())    // role name string
                .build();
    }

    @Transactional
    public void sendTeamInvitationEmail(String email, String roleName, String organizationName, User currentUser, Tenant tenant) throws MessagingException {

        TeamInvitation invitation = new TeamInvitation();
        Role role = roleRepo.findByName(roleName);

        if(role == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }

        invitation.setEmail(email);
        invitation.setTenant(tenant);
        invitation.setRole(role);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setExpiresAt(LocalDateTime.now().plusHours(INVITE_EXPIRY_HOURS));
        invitation.setInvitedBy(currentUser);
        // the User doing the inviting

        teamInvitationRepo.save(invitation);

        String invitationLink = appBaseUrl + "/invite/accept?token=" + invitation.getToken();
        Context context = new Context();
        context.setVariable("inviterName", currentUser.getFullName());
        context.setVariable("organizationName", organizationName);
        context.setVariable("role", roleName);
        context.setVariable("invitationLink", invitationLink);
        context.setVariable("expiryHours", INVITE_EXPIRY_HOURS);

        String htmlBody = templateEngine.process("team-invitation-email", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(email);
        helper.setSubject(currentUser.getFullName() + " invited you to join " + organizationName + " on InvoiceHub");
        helper.setText(htmlBody, true);

        mailSender.send(message);
    }

    @Transactional
    public User createTeamMember(String fullName, String password, String token) {

        TeamInvitation teamInvite = teamInvitationRepo.findByTokenAndStatus(token, InvitationStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team invitation not found"));

        // Reject stale links (and flip the row to EXPIRED so it can't be retried).
        if (teamInvite.getExpiresAt().isBefore(LocalDateTime.now())) {
            teamInvite.setStatus(InvitationStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.GONE, "This invitation has expired");
        }

        User user = new User();
        user.setFullName(fullName);
        user.setPassword(encoder.encode(password));
        user.setRole(teamInvite.getRole());
        user.setTenant(teamInvite.getTenant());
        user.setEmail(teamInvite.getEmail());
        user = userRepo.save(user);

        // Consume the invite so the token can't be reused.
        teamInvite.setStatus(InvitationStatus.ACCEPTED);
        teamInvite.setAcceptedAt(LocalDateTime.now());

        return user;
    }
}
