package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.Getter;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
import org.springframework.stereotype.Service;

@Service
public class LockManager {
    private final @Getter IdBasedLockManager<VirtualWallet> virtualWalletLockManager = new SafeIdBasedLockManager<>();
    private final @Getter IdBasedLockManager<Currency> currencyLockManager = new SafeIdBasedLockManager<>();
    private final @Getter IdBasedLockManager<TradingPair> tradingPairLockManager = new SafeIdBasedLockManager<>();
}
