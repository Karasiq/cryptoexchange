package com.springapp.cryptoexchange.database.model;


import com.springapp.cryptoexchange.config.ServerSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.*;
import javax.xml.bind.DatatypeConverter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@Table(name = "accounts")
@EqualsAndHashCode(of={"id", "login", "passwordHash"})
@ToString(of={"id", "enabled", "login", "passwordHash", "emailAddress"})
@Transactional
public class Account implements Serializable {
    @Id
    @Column(unique = true)
    @GeneratedValue
    private long id;

    @Column(unique = true, length = 30, name = "login")
    private String login;

    @Column(length = 200, name = "password")
    private String passwordHash;

    @Column(length = 200, name = "email_address")
    private String emailAddress;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "account")
    final Set<VirtualWallet> virtualWalletMap = new HashSet<>();

    public Account(final String login, final String password) throws Exception {
        this.login = login;
        this.passwordHash = generatePasswordHash(password);
    }

    public static boolean validate(String login, String password) {
        return login.length() < 30 && login.length() > 0 && password.length() >= 8 && password.length() < 200;
    }

    public static String generatePasswordHash(String password) throws Exception {
        final String hashingAlgorithm = "HmacSHA512";
        SecretKeySpec key = new SecretKeySpec(ServerSettings.serverSettings.getHashingSalt().getBytes("UTF-8"), hashingAlgorithm);
        Mac mac = Mac.getInstance(hashingAlgorithm);
        mac.init(key);
        return DatatypeConverter.printHexBinary(mac.doFinal(password.getBytes("UTF-8"))).toLowerCase();
    }

    public boolean checkPassword(String password) throws Exception {
        return generatePasswordHash(password).equals(passwordHash);
    }

    @Transactional
    public VirtualWallet createVirtualWallet(Currency currency) {
        VirtualWallet v = new VirtualWallet(currency, this);
        synchronized (virtualWalletMap) {
            if(!virtualWalletMap.contains(v)) {
                virtualWalletMap.add(v);
            } else {
                for(VirtualWallet wallet : virtualWalletMap) if (wallet.equals(v)) {
                    return wallet;
                }
            }
        }
        return v;
    }

    @Transactional
    public VirtualWallet getBalance(Currency currency) {
        synchronized (virtualWalletMap) {
            for(VirtualWallet wallet : virtualWalletMap) {
                if(wallet.getCurrency().equals(currency)) {
                    return wallet;
                }
            }
        }
        return null;
    }
}
