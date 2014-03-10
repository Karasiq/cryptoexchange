package com.springapp.cryptoexchange.database.model;

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
public class Daemon implements Serializable {
    @Column
    @GeneratedValue
    @Id
    long id;

    @OneToOne
    @NonNull Currency currency;

    @Column(name = "daemon_host")
    @NonNull String daemonHost;

    @Column(name = "daemon_port")
    @NonNull Integer daemonPort;

    @Column(name = "daemon_login")
    @NonNull String daemonLogin;

    @Column(name = "daemon_password")
    @NonNull String daemonPassword;
}
