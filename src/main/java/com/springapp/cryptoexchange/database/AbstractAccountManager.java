package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface AbstractAccountManager {
    public static class AccountException extends Exception {
        public AccountException(String message) {
            super(String.format("Account exception: %s", message));
        }
        public AccountException(Throwable throwable) {
            super(String.format("Account exception (%s)", throwable.getLocalizedMessage()), throwable);
        }
    }
    public VirtualWallet getVirtualWallet(Account account, Currency currency) throws Exception;
    public Account addAccount(Account account) throws Exception;
    public Account getAccount(String login);
    public void setAccountEnabled(long id, boolean enabled);
    public void withdrawFunds(VirtualWallet wallet, String address, BigDecimal amount) throws Exception;
    public void login(String login, String password, String ip, String browserFingerprint) throws Exception;
}
