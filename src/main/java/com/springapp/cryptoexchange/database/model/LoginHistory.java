package com.springapp.cryptoexchange.database.model;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "login_history")
@Data
@EqualsAndHashCode(exclude = "account")
@ToString(exclude = "account")
@RequiredArgsConstructor
public class LoginHistory implements Serializable { // Log
    @Id
    @GeneratedValue
    @Column(unique = true)
    long id;

    @Column(name = "login_time")
    Date time = new Date();

    @Column(name = "login_ip", length = 30)
    @NonNull String ip;

    @Column(name = "browser_fingerprint")
    @NonNull String fingerprint;

    @ManyToOne
    @NonNull Account account;
}
