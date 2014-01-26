package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@NoArgsConstructor
public class LoginHistory implements Serializable { // Log
    @Id
    @GeneratedValue
    @Column(unique = true)
    @JsonIgnore
    long id;

    @Column(name = "login_time")
    Date time = new Date();

    @Column(name = "login_ip", length = 30)
    @NonNull String ip;

    @Column(name = "browser_fingerprint")
    @JsonIgnore
    @NonNull String fingerprint;

    @ManyToOne
    @JsonIgnore
    @NonNull Account account;
}
