package com.springapp.cryptoexchange.database.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "daemons")
@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class Daemon implements Serializable {
    @Column
    @GeneratedValue
    @Id
    private long id;

    @OneToOne
    private @NonNull Currency currency;

    @Column(name = "daemon_host")
    private @NonNull String daemonHost = "localhost";

    @Column(name = "daemon_port")
    private @NonNull Integer daemonPort;

    @Column(name = "daemon_login")
    private @NonNull String daemonLogin;

    @Column(name = "daemon_password")
    private @NonNull String daemonPassword;
}
