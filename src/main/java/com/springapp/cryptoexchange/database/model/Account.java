package com.springapp.cryptoexchange.database.model;


import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.Assert;

import javax.persistence.*;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@Table(name = "accounts", indexes = {
        @Index(columnList = "login, email_address", unique = true)
})
@ToString(exclude = {"passwordHash", "googleAuthSecret"})
@EqualsAndHashCode(of = {"login"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Account implements Serializable {
    private final static GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    public static enum Role {
        ANONYMOUS, USER, MODERATOR, ADMIN, API_USER;

        static {
            USER.setSubRoles(API_USER);
            MODERATOR.setSubRoles(USER);
            ADMIN.setSubRoles(MODERATOR);
        }

        private final Set<Role> subRoles = new HashSet<>();

        public List<GrantedAuthority> getGrantedAuthorities() {
            final List<GrantedAuthority> grantedAuthorities = new ArrayList<>(subRoles.size() + 1);
            grantedAuthorities.add(this.toGrantedAuthority());
            for(Role subRole : subRoles) grantedAuthorities.add(subRole.toGrantedAuthority());
            return grantedAuthorities;
        }

        private GrantedAuthority toGrantedAuthority() {
            return new SimpleGrantedAuthority("ROLE_".concat(toString()));
        }

        private void setSubRoles(Role... subRoles) {
            for(Role subRole : subRoles) {
                this.subRoles.add(subRole);
                this.subRoles.addAll(subRole.subRoles);
            }
        }
    }

    @Id
    @GeneratedValue
    long id;

    @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]{4,29}", message = "Invalid username")
    @Column(length = 30, name = "login", unique = true, updatable = false)
    String login;

    @Column(length = 200, name = "password")
    String passwordHash;

    @Pattern(regexp = "[a-zA-Z0-9._%+-]+@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}", message = "Invalid e-mail address")
    @Column(length = 200, name = "email_address", unique = true)
    String emailAddress;

    @Column(name = "enabled", nullable = false)
    boolean enabled = true;

    @Column(name = "role", nullable = false)
    Role role = Role.USER;

    @Column(name = "google_auth_secret", unique = true)
    String googleAuthSecret;

    public void checkGoogleAuth(int code) {
        if(googleAuthSecret != null) {
            Assert.isTrue(googleAuthenticator.authorize(getGoogleAuthSecret(), code), "Invalid two factor auth code");
        }
    }

    public GoogleAuthenticatorKey generateGoogleAuthSecret() {
        Assert.isNull(getGoogleAuthSecret(), "Two factor auth secret already generated");
        GoogleAuthenticatorKey googleAuthenticatorKey = googleAuthenticator.createCredentials();
        Assert.hasLength(googleAuthenticatorKey.getKey());
        setGoogleAuthSecret(googleAuthenticatorKey.getKey());
        return googleAuthenticatorKey;
    }

    public Account(final String login, final String emailAddress, final String password) throws Exception {
        Assert.isTrue(password.matches("[a-zA-Z0-9_!@#$%^&*]{6,200}"), "Invalid password");
        setLogin(login);
        setEmailAddress(emailAddress);
        setPasswordHash(generatePasswordHash(login, password));
    }

    public static String generatePasswordHash(String login, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        ShaPasswordEncoder passwordEncoder = new ShaPasswordEncoder(256);
        return passwordEncoder.encodePassword(password, login);
    }
}
