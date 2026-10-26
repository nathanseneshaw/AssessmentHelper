package edu.utdallas.cs4485.assessmenthelper.security;

import java.util.Collection;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AccountPrincipal implements UserDetails, CredentialsContainer {

    private final CurrentUser user;
    private final boolean active;
    private String passwordHash;

    public AccountPrincipal(CurrentUser user, String passwordHash, boolean active) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public CurrentUser user() {
        return user;
    }

    public boolean isCommitteeMember() {
        return user.roles().contains(Role.AC);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
