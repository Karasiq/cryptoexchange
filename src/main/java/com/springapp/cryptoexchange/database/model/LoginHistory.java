package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "login_history")
@Data
@RequiredArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Immutable
public class LoginHistory implements Serializable { // Log
    @Id
    @GeneratedValue
    @Column(unique = true)
    @JsonIgnore
    long id;

    @Column(name = "login_time")
    Date time = new Date();

    @ManyToOne
    @JsonIgnore
    @NonNull Account account;

    @Column(name = "login_ip", length = 30)
    @NonNull String ip;

    @Column(name = "user_agent")
    @NonNull String userAgentString;
}
