package com.springapp.cryptoexchange.database.model;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "currency_types")
@EqualsAndHashCode(of = {"currencyCode", "currencyName"})
@ToString(of = {"id", "currencyCode", "currencyName"}, callSuper = false)
public class Currency implements Serializable {
    @Id
    @GeneratedValue
    @Column(unique = true)
    private long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "code", nullable = false, unique = true)
    private @NonNull String currencyCode;

    @Column(name = "name", nullable = false, unique = true)
    private @NonNull String currencyName;

    @Column(name = "withdraw_fee", nullable = false)
    private BigDecimal withdrawFee = new BigDecimal(3);

    // Daemon:

    @Column(name = "daemon_host")
    private @NonNull String daemonHost = "localhost";

    @Column(name = "daemon_port")
    private @NonNull Integer daemonPort;

    @Column(name = "daemon_login")
    private @NonNull String daemonLogin;

    @Column(name = "daemon_password")
    private @NonNull String daemonPassword;
}
