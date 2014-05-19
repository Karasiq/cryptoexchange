package com.bitcoin.daemon;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.util.Assert;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@CommonsLog
@Value
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CryptoCoinWallet implements AbstractWallet<String, Address.Transaction>, Closeable {
    @Override
    public void close() throws IOException {
        jsonRPC.close();
    }

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
    final Map<String, Address.Transaction> transactionMap = new HashMap<>();
    final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private BigDecimal getReceivedByAddress(String address) throws Exception { // Not cached
        return jsonRPC.executeRpcRequest("getreceivedbyaddress", Arrays.asList(address, Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<BigDecimal>>(){});
    }

    public BigDecimal getAddressBalance(String address) throws Exception { // Cached
        Assert.hasLength(address, "Address can not be empty");
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            Address addressCache = addressList.get(address);
            if (addressCache == null) {
                addressCache = new Address(address);
                addressCache.setReceivedByAddress(getReceivedByAddress(address));
                Lock writeLock = readWriteLock.writeLock();
                writeLock.lock();
                try {
                    addressList.put(address, addressCache);
                } finally {
                    writeLock.unlock();
                }
            }
            return addressCache.getReceivedByAddress();
        } finally {
            readLock.unlock();
        }
    }

    public List<Address.Transaction> getTransactions() throws Exception {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            final Set<Address.Transaction> transactionSet = new HashSet<>(transactionMap.values());
            for(Address address : addressList.values()) transactionSet.addAll(address.getTransactionSet());
            return new ArrayList<>(transactionSet);
        } finally {
            readLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Address.Transaction> getTransactions(final Set<String> addresses) throws Exception {
        final List<Address.Transaction> transactionList = new ArrayList<>();
        if (addresses.size() < 1) {
            return transactionList;
        }
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            for(String strAddress : addresses) if(addressList.containsKey(strAddress)) {
                transactionList.addAll(addressList.get(strAddress).getTransactionSet());
            }
            return transactionList;
        } finally {
            readLock.unlock();
        }
    }

    public Address.Transaction getTransaction(String transactionId) throws Exception {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            Address.Transaction transaction = transactionMap.get(transactionId);
            if (transaction == null) {
                transaction = jsonRPC.executeRpcRequest("gettransaction", Arrays.asList(transactionId), new TypeReference<JsonRPC.Response<Address.Transaction>>(){});
                Assert.isTrue(transaction.getTxid().equals(transactionId));
                Lock writeLock = readWriteLock.writeLock();
                writeLock.lock();
                try {
                    transactionMap.put(transaction.getTxid(), transaction);
                } finally {
                    writeLock.unlock();
                }
            }
            return transaction;
        } finally {
            readLock.unlock();
        }
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

        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            for(String strAddress : addresses) confirmed = confirmed.add(getAddressBalance(strAddress));
        } finally {
            readLock.unlock();
        }
        return confirmed;
    }

    public BigDecimal summaryConfirmedBalance() throws Exception {
        // return getDefaultAccount().summaryConfirmedBalance();
        return BigDecimal.valueOf((Double) getInfo().get("balance")).setScale(8);
    }

    private Map<String, BigDecimal> listReceivedByAddress() throws Exception {
        final List<Address.Transaction> output = jsonRPC.executeRpcRequest("listreceivedbyaddress", Arrays.asList(Settings.REQUIRED_CONFIRMATIONS, true), new TypeReference<JsonRPC.Response<List<Address.Transaction>>>(){});

        final Map<String, BigDecimal> result = new HashMap<>(output.size());
        for(Address.Transaction transaction : output) result.put(transaction.address, transaction.amount);
        return result;
    }

    public void loadTransactions(int maxCount) throws Exception { // Expensive
        // loadAddresses();
        final List<Address.Transaction> transactions = getDefaultAccount().listTransactions(maxCount);
        final Map<String, BigDecimal> receivedByAddress = listReceivedByAddress();

        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            if (transactionMap.size() > maxCount * 3) { // Cache clean
                transactionMap.clear();
                addressList.clear();
            }
            Address address = null;
            for(Map.Entry<String, BigDecimal> entry : receivedByAddress.entrySet()) {
                if(address == null || !address.getAddress().equals(entry.getKey())) {
                    address = addressList.get(entry.getKey());
                    if(address == null) {
                        address = new Address(entry.getKey());
                        addressList.put(entry.getKey(), address);
                    }
                    address.setReceivedByAddress(entry.getValue());
                }
            }
            for(Address.Transaction transaction : transactions) {
                Assert.hasLength(transaction.getTxid(), "Invalid transaction: " + transaction);
                transactionMap.put(transaction.getTxid(), transaction);
                if(transaction.getAddress() != null && transaction.getAddress().length() > 0 && "receive".equals(transaction.category)) {
                    if(address == null || !address.getAddress().equals(transaction.address)) {
                        address = addressList.get(transaction.address);
                        if(address == null) {
                            address = new Address(transaction.address);
                            addressList.put(transaction.address, address);
                        }
                    }
                    address.addTransaction(transaction);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Set<String> getAddressSet() throws Exception {
        return Collections.unmodifiableSet(addressList.keySet());
        // return getDefaultAccount().getAddressSet();
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
