package com.bitcoin.daemon;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.*;

@CommonsLog
public class CryptoCoinWallet {
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Account implements AbstractWallet {
        public static String generateUniqueAccountName() {
            return UUID.randomUUID().toString();
        }

        @NonNull JsonRPC jsonRPC;
        @NonNull String name;
        Map<String, Address> addressList = new HashMap<>();

        public BigDecimal summaryConfirmedBalance() throws Exception {
            List<Object> args = new ArrayList<>(2);
            args.add(getName());
            args.add(Settings.REQUIRED_CONFIRMATIONS);
            return jsonRPC.executeRpcRequest("getreceivedbyaccount", args, new TypeReference<JsonRPC.Response<BigDecimal>>(){});
        }

        public BigDecimal getReceivedByAddress(String address) throws Exception {
            Assert.hasLength(address, "Address can not be empty");
            final List<Object> args = new ArrayList<>(2);
            args.add(address);
            args.add(Settings.REQUIRED_CONFIRMATIONS);
            return jsonRPC.executeRpcRequest("getreceivedbyaddress", args, new TypeReference<JsonRPC.Response<BigDecimal>>(){});
        }

        public List<Address.Transaction> getTransactions() throws Exception {
            List<Address.Transaction> transactionList = new ArrayList<>(5);
            synchronized (addressList) {
                for(Address address : addressList.values()) {
                    transactionList.addAll(address.getTransactionList().values());
                }
            }
            return transactionList;
        }

        @SuppressWarnings("unchecked")
        public List<Address.Transaction> getTransactions(final Set addresses) throws Exception {
            final List<Address.Transaction> transactionList = new ArrayList<>(5);
            if (addresses.size() < 1) {
                return transactionList;
            }
            synchronized (addressList) {
                for(String strAddress : (Set<String>) addresses) {
                    Address address = addressList.get(strAddress);
                    if(address != null) transactionList.addAll(address.getTransactionList().values());
                }
            }
            return transactionList;
        }

        public Address.Transaction getTransaction(String transactionId) throws Exception {
            List<Object> args = new ArrayList<>(1);
            args.add(transactionId);
            return jsonRPC.executeRpcRequest("gettransaction", args, new TypeReference<JsonRPC.Response<Address.Transaction>>(){});
        }

        @SuppressWarnings("unchecked")
        public BigDecimal summaryConfirmedBalance(final Set addresses) throws Exception {
            BigDecimal confirmed = BigDecimal.ZERO;
            if (addresses.size() < 1) {
                return confirmed; // 0
            }
            synchronized (addressList) {
                for(String strAddress : (Set<String>) addresses) {
                    confirmed = confirmed.add(getReceivedByAddress(strAddress));
                }
            }
            return confirmed;
        }

        public void loadAddresses() throws Exception {
            List<Object> args = new ArrayList<>(1);
            args.add(this.getName());
            List<String> addresses = jsonRPC.executeRpcRequest("getaddressesbyaccount", args, new TypeReference<JsonRPC.Response<List<String>>>(){});
            synchronized (addressList) {
                for(String address : addresses) if(!addressList.containsKey(address)) {
                    addressList.put(address, new Address(address));
                }
            }
        }

        public void loadTransactions(int maxCount) throws Exception { // Expensive
            // loadAddresses();
            List<Object> args = new ArrayList<>(2);
            args.add(this.getName());
            args.add(maxCount); // tx count
            // args.add(confirmedTxCount); // tx from

            List<Address.Transaction> transactions = jsonRPC.executeRpcRequest("listtransactions", args, new TypeReference<JsonRPC.Response<List<Address.Transaction>>>(){});

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
                    address.addTransaction(transaction);
                }
            }
        }

        public boolean importPrivateKey(String privateKey) throws Exception { // Unsafe
            Assert.hasLength(privateKey, "Private key can not be empty");
            List<Object> args = new ArrayList<>(2);
            args.add(privateKey);
            args.add(this.getName());
            return jsonRPC.executeRpcRequest("importprivkey", args, new TypeReference<JsonRPC.Response<Boolean>>() {});
        }

        public Address getRandomAddress() {
            synchronized (addressList) {
                Iterator<Address> iterator = addressList.values().iterator();
                return iterator.hasNext() ? iterator.next() : null;
            }
        }

        public Address generateNewAddress() throws Exception {
            List<Object> args = new ArrayList<>(1);
            args.add(this.getName());
            String response = jsonRPC.executeRpcRequest("getnewaddress", args, new TypeReference<JsonRPC.Response<String>>(){});
            Address address = new Address(response);
            synchronized (addressList) {
                addressList.put(response, address);
            }
            return address;
        }

        public Address.Transaction sendToAddress(String address, @NonNull BigDecimal amount) throws Exception {
            Assert.hasLength(address, "Address cannot be empty");
            Assert.isTrue(amount.compareTo(BigDecimal.ZERO) > 0, "Amount must be greater than zero");
            final List<Object> args = new ArrayList<>(4);
            args.add(this.getName());
            args.add(address);
            args.add(amount);
            args.add(Settings.REQUIRED_CONFIRMATIONS); // minconf
            String txid = jsonRPC.executeRpcRequest("sendfrom", args, new TypeReference<JsonRPC.Response<String>>(){});
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
    }

    public static Account generateAccount(final JsonRPC jsonRPC, String prefix) throws Exception {
        final String uuid = String.format("%s-%s", prefix, Account.generateUniqueAccountName());
        final Account account = new Account(jsonRPC, uuid);
        account.generateNewAddress();
        return account;
    }

    public static Account getDefaultAccount(final JsonRPC jsonRPC) {
        return new Account(jsonRPC, Settings.DEFAULT_ACCOUNT);
    }
}
