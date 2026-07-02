package com.adsyahir.invoice_hub_backend.model;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private User user;
    public UserPrincipal(User user) {
        this.user = user;
    }

    /** The wrapped domain user (role, tenant, profile). */
    public User getUser() {
        return user;
    }

    /**
     * Authorities = the role's permissions (e.g. "invoice:write") plus a
     * conventional "ROLE_&lt;name&gt;" authority, so both
     * {@code hasAuthority('invoice:write')} and {@code hasRole('TENANT_ADMIN')}
     * work in @PreAuthorize.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role role = user.getRole();
        if (role == null) {
            return Collections.emptyList();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        role.getPermissions().forEach(p ->
                authorities.add(new SimpleGrantedAuthority(p.getName())));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return (String) user.getPassword();
    }

    // Spring Security's "username" is just the principal identifier — we use email.
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
