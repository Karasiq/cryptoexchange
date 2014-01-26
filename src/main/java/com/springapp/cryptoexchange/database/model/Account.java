package com.springapp.cryptoexchange.database.model;


import com.springapp.cryptoexchange.config.ServerSettings;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.*;
import javax.xml.bind.DatatypeConverter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@Table(name = "accounts")
@EqualsAndHashCode(of={"id", "login", "passwordHash"})
@ToString(exclude = "virtualWalletMap")
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

    @OneToOne(fetch = FetchType.EAGER)
    private Role role;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    final Set<VirtualWallet> virtualWalletMap = new HashSet<>();

    public Account(final String login, final String password) throws Exception {
        this.login = login;
        this.passwordHash = generatePasswordHash(login, password);
    }

    public static String generatePasswordHash(String login, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final String hashingAlgorithm = "SHA-256";
        MessageDigest md = MessageDigest.getInstance(hashingAlgorithm);
        return DatatypeConverter.printHexBinary(md.digest((password + login).getBytes("UTF-8"))).toLowerCase();
    }

    public boolean checkPassword(String password) throws Exception {
        return generatePasswordHash(this.login, password).equals(passwordHash);
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