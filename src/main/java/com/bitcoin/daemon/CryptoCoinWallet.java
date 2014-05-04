package com.bitcoin.daemon;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@CommonsLog
@Value
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CryptoCoinWallet implements AbstractWallet<String, Address.Transaction> {
    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public class Account {
        String name;

        public BigDecimal summaryConfirmedBalance() throws Exception {
            return jsonRPC.executeRpcRequest("getbalance", Arrays.asList(getName(), Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<BigDecimal>>(){});
        }

        public Set<String> getAddressSet() throws Exception {
            return jsonRPC.executeRpcRequest("getaddressesbyaccount", Arrays.asList(this.getName()), new TypeReference<JsonRPC.Response<Set<String>>>(){});
        }

        public String generateNewAddress() throws Exception {
            return jsonRPC.executeRpcRequest("getnewaddress", Arrays.asList(this.getName()), new TypeReference<JsonRPC.Response<String>>(){});
        }

        public Address.Transaction sendFromAccount(String address, @NonNull BigDecimal amount) throws Exception {
            Assert.hasLength(address, "Address cannot be empty");
            Assert.isTrue(amount.compareTo(BigDecimal.ZERO) > 0, "Amount must be greater than zero");
            String txid = jsonRPC.executeRpcRequest("sendfrom", Arrays.asList(this.getName(), address, amount, Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<String>>(){});
            try {
                return getTransaction(txid);
            } catch (Exception e) {
                log.debug(e.getStackTrace());
                log.fatal(e);
                // Try to manually recover
                final Address.Transaction transaction = new Address.Transaction();
                transaction.setTxid(txid);
                transaction.setAmount(amount.negate());
                transaction.setCategory("send");
                return transaction;
            }
        }

        public List<Address.Transaction> listTransactions(int maxCount) throws Exception {
            return jsonRPC.executeRpcRequest("listtransactions", Arrays.asList(this.getName(), maxCount), new TypeReference<JsonRPC.Response<List<Address.Transaction>>>(){});
        }
    }

    JsonRPC jsonRPC;
    final Map<String, Address> addressList = new HashMap<>();

    private BigDecimal getReceivedByAddress(String address) throws Exception { // Not cached
        return jsonRPC.executeRpcRequest("getreceivedbyaddress", Arrays.asList(address, Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<BigDecimal>>(){});
    }

    public BigDecimal getAddressBalance(String address) throws Exception { // Cached
        Assert.hasLength(address, "Address can not be empty");
        synchronized (addressList) {
            Address addressCache = addressList.get(address);
            if (addressCache == null) {
                addressCache = new Address(address);
                addressList.put(address, addressCache);
            }
            if (addressCache.getReceivedByAddress().equals(BigDecimal.ZERO)) { // Unset (default value)
                addressCache.setReceivedByAddress(getReceivedByAddress(address));
            }
            return addressCache.getReceivedByAddress();
        }
    }

    public List<Address.Transaction> getTransactions() throws Exception {
        List<Address.Transaction> transactionList = new ArrayList<>(5);
        synchronized (addressList) {
            for(Address address : addressList.values()) {
                transactionList.addAll(address.getTransactionSet());
            }
        }
        return transactionList;
    }

    @SuppressWarnings("unchecked")
    public List<Address.Transaction> getTransactions(final Set<String> addresses) throws Exception {
        final List<Address.Transaction> transactionList = new ArrayList<>(5);
        if (addresses.size() < 1) {
            return transactionList;
        }
        synchronized (addressList) {
            for(String strAddress : addresses) if(addressList.containsKey(strAddress)) {
                transactionList.addAll(addressList.get(strAddress).getTransactionSet());
            }
        }
        return transactionList;
    }

    public Address.Transaction getTransaction(String transactionId) throws Exception {
        return jsonRPC.executeRpcRequest("gettransaction", Arrays.asList(transactionId), new TypeReference<JsonRPC.Response<Address.Transaction>>(){});
    }

    public Map<String, ?> getInfo() throws Exception {
        return jsonRPC.executeRpcRequest("getinfo", null, new TypeReference<JsonRPC.Response<Map<String, Object>>>(){});
    }

    @SuppressWarnings("unchecked")
    public BigDecimal summaryConfirmedBalance(final Set<String> addresses) throws Exception {
        BigDecimal confirmed = BigDecimal.ZERO;
        if (addresses.size() < 1) {
            return confirmed; // 0
        }

        for(String strAddress : addresses) confirmed = confirmed.add(getAddressBalance(strAddress));
        return confirmed;
    }

    public BigDecimal summaryConfirmedBalance() throws Exception {
        // return getDefaultAccount().summaryConfirmedBalance();
        return BigDecimal.valueOf((Double) getInfo().get("balance")).setScale(8);
    }

    private Map<String, BigDecimal> listReceivedByAddress() throws Exception {
        final List<Address.Transaction> output = jsonRPC.executeRpcRequest("listreceivedbyaddress", Arrays.asList(Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<List<Address.Transaction>>>(){});

        final Map<String, BigDecimal> result = new HashMap<>();
        for(Address.Transaction transaction : output) result.put(transaction.address, transaction.amount);
        return result;
    }

    public void loadTransactions(int maxCount) throws Exception { // Expensive
        // loadAddresses();
        final List<Address.Transaction> transactions = getDefaultAccount().listTransactions(maxCount);
        final Map<String, BigDecimal> receivedByAddress = listReceivedByAddress();

        synchronized (addressList) {
            addressList.clear(); // flush
            Address address = null;
            for(Address.Transaction transaction : transactions) if(transaction.getAddress() != null
                    && transaction.getAddress().length() > 0 && "receive".equals(transaction.category))
            {
                if(address == null || !address.getAddress().equals(transaction.address)) {
                    if(addressList.containsKey(transaction.address)) {
                        address = addressList.get(transaction.address);
                    } else {
                        address = new Address(transaction.address);
                        addressList.put(transaction.address, address);
                    }
                }
                address.setReceivedByAddress(receivedByAddress.getOrDefault(transaction.address, BigDecimal.ZERO));
                address.addTransaction(transaction);
            }
        }
    }

    public Set<String> getAddressSet() throws Exception {
        return getDefaultAccount().getAddressSet();
    }

    public Address.Transaction sendToAddress(String address, @NonNull BigDecimal amount) throws Exception {
        Assert.hasLength(address, "Address cannot be empty");
        Assert.isTrue(amount.compareTo(BigDecimal.ZERO) > 0, "Amount must be greater than zero");
        String txid = jsonRPC.executeRpcRequest("sendtoaddress", Arrays.asList(address, amount), new TypeReference<JsonRPC.Response<String>>(){});
        try {
            return getTransaction(txid);
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.fatal(e);
            // Try to manually recover
            final Address.Transaction transaction = new Address.Transaction();
            transaction.setTxid(txid);
            transaction.setAmount(amount.negate());
            transaction.setCategory("send");
            return transaction;
        }
    }

    public String generateNewAddress() throws Exception {
        return getDefaultAccount().generateNewAddress();
    }

    public List<Account> getAccountList() throws Exception {
        final Map<String, BigDecimal> result = jsonRPC.executeRpcRequest("listaccounts", null, new TypeReference<JsonRPC.Response<Map<String, BigDecimal>>>(){});
        final List<Account> accountList = new ArrayList<>(result.size());
        for(String accountName : result.keySet()) {
            accountList.add(getAccount(accountName));
        }
        return accountList;
    }

    private static String generateUniqueAccountName() {
        return UUID.randomUUID().toString();
    }

    public Account generateAccount(String prefix) throws Exception {
        final String uuid = String.format("%s-%s", prefix, generateUniqueAccountName());
        final Account account = getAccount(uuid);
        account.generateNewAddress();
        return account;
    }

    public Account getDefaultAccount() {
        return getAccount(Settings.DEFAULT_ACCOUNT);
    }

    public Account getAccount(String accountName) {
        return new Account(accountName);
    }
}
