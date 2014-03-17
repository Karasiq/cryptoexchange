package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "daemons")
@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@EqualsAndHashCode(of={"daemonHost", "daemonPort", "daemonLogin", "daemonPassword"})
public class Daemon implements Serializable {
    @GeneratedValue
    @Id
    long id;

    @OneToOne(fetch = FetchType.EAGER)
    @NonNull Currency currency;

    @JsonIgnore
    @Column(name = "daemon_host")
    @NonNull String daemonHost;

    @JsonIgnore
    @Column(name = "daemon_port")
    @NonNull Integer daemonPort;

    @JsonIgnore
    @Column(name = "daemon_login")
    @NonNull String daemonLogin;

    @JsonIgnore
    @Column(name = "daemon_password")
    @NonNull String daemonPassword;
}
