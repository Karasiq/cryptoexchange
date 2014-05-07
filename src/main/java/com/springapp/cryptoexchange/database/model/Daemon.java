package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Table(name = "daemons")
@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@EqualsAndHashCode(of={"daemonHost", "daemonPort", "daemonLogin", "daemonPassword"})
@ToString(of = {"id", "currency"})
public class Daemon implements Serializable {
    @GeneratedValue
    @Id
    long id;

    @OneToOne(fetch = FetchType.EAGER)
    @NonNull Currency currency;

    @Size(min = 1)
    @JsonIgnore
    @Column(name = "daemon_host")
    @NonNull String daemonHost;

    @Min(1)
    @Max(65535)
    @JsonIgnore
    @Column(name = "daemon_port")
    @NonNull Integer daemonPort;

    @Size(min = 1)
    @JsonIgnore
    @Column(name = "daemon_login")
    @NonNull String daemonLogin;

    @Size(min = 1)
    @JsonIgnore
    @Column(name = "daemon_password")
    @NonNull String daemonPassword;
}
