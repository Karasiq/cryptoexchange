package com.springapp.cryptoexchange.database.model;


import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.persistence.*;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Data
@Entity
@NoArgsConstructor
@Table(name = "accounts")
@ToString(exclude = {"passwordHash"})
@EqualsAndHashCode(of = {"id", "login", "emailAddress", "passwordHash", "role"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Account implements Serializable {
    public static enum RoleClass {
        ANONYMOUS, USER, MODERATOR, ADMIN, API_USER;

        static {
            USER.setSubRoles(API_USER);
            MODERATOR.setSubRoles(USER);
            ADMIN.setSubRoles(MODERATOR);
        }

        private final Set<RoleClass> subRoles = new HashSet<>();

        public List<GrantedAuthority> getGrantedAuthorities() {
            final List<GrantedAuthority> grantedAuthorities = new ArrayList<>(subRoles.size() + 1);
            grantedAuthorities.add(this.toGrantedAuthority());
            for(RoleClass subRole : subRoles) grantedAuthorities.add(subRole.toGrantedAuthority());
            return grantedAuthorities;
        }

        private GrantedAuthority toGrantedAuthority() {
            return new SimpleGrantedAuthority("ROLE_".concat(toString()));
        }

        private void setSubRoles(RoleClass... subRoles) {
            for(RoleClass subRole : subRoles) {
                this.subRoles.add(subRole);
                this.subRoles.addAll(subRole.subRoles);
            }
        }
    }

    @Id
    @GeneratedValue
    long id;

    @Column(length = 30, name = "login", unique = true, updatable = false)
    String login;

    @Column(length = 200, name = "password")
    String passwordHash;

    @Column(length = 200, name = "email_address", unique = true)
    String emailAddress;

    @Column(name = "enabled", nullable = false)
    boolean enabled = true;

    @Column(name = "role", nullable = false)
    RoleClass role = RoleClass.USER;

    public static boolean validate(final String login, final String password, final String emailAddress) {
        return emailAddress.matches("[a-zA-Z0-9._%+-]+@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}") &&
                login.matches("[a-zA-Z][a-zA-Z0-9_-]{4,29}") &&
                password.matches("[a-zA-Z0-9_!@#$%^&*]{6,200}");
    }

    public Account(final String login, final String emailAddress, final String password) throws Exception {
        setLogin(login);
        setEmailAddress(emailAddress);
        setPasswordHash(generatePasswordHash(login, password));
    }

    public static String generatePasswordHash(String login, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        ShaPasswordEncoder passwordEncoder = new ShaPasswordEncoder(256);
        return passwordEncoder.encodePassword(password, login);
    }
}
