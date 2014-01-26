package com.springapp.cryptoexchange.database.model;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Entity
@Table(name = "roles")
public class Role {
    public static enum RoleClass {
        ANONYMOUS, USER, MODERATOR, ADMIN;
        public List<GrantedAuthority> getGrantedAuthorities() {
            List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
            switch (this) {
                case ANONYMOUS:
                    grantedAuthorities.add(new SimpleGrantedAuthority(ANONYMOUS.toString()));
                    break;
                case USER:
                    grantedAuthorities.add(new SimpleGrantedAuthority(USER.toString()));
                    break;
                case MODERATOR:
                    grantedAuthorities.add(new SimpleGrantedAuthority(USER.toString()));
                    grantedAuthorities.add(new SimpleGrantedAuthority(MODERATOR.toString()));
                    break;
                case ADMIN:
                    grantedAuthorities.add(new SimpleGrantedAuthority(USER.toString()));
                    grantedAuthorities.add(new SimpleGrantedAuthority(MODERATOR.toString()));
                    grantedAuthorities.add(new SimpleGrantedAuthority(ADMIN.toString()));
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            return grantedAuthorities;
        }
    }
    @Id
    @GeneratedValue
    @Column(unique = true)
    private long id;

    @Column(name = "role_class", unique = true)
    private RoleClass roleClass;

    @OneToMany(fetch = FetchType.LAZY)
    private Set<Account> roleAccounts;
}
