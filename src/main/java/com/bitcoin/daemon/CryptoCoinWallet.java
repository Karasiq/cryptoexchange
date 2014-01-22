package com.bitcoin.daemon;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.type.TypeReference;

import java.math.BigDecimal;
import java.util.*;

public class CryptoCoinWallet {
    public static final int REQUIRED_CONFIRMATIONS = 6;

    public
    @Data
    static class Account {
        public Account(String name) {
            this.name = name;
        }

        public static String generateUniqueAccountName() {
            return UUID.randomUUID().toString();
        }

        private String name;

        @Data public static class Address {
            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            @EqualsAndHashCode(exclude = {"confirmations"})
            public static class Transaction {
                String account;
                String address;
                long time;
                String txid;
                BigDecimal amount;
                BigDecimal fee;
                String category; // send/receive
                int confirmations;

                public boolean isConfirmed() {
                    return confirmations >= REQUIRED_CONFIRMATIONS;
                }
            }
            private final HashMap<Integer, Transaction> transactionList = new HashMap<Integer, Transaction>();
            public void addTransaction(Transaction transaction) {
                synchronized (transactionList) {
                    int txHashCode = transaction.hashCode();
                    if(!transactionList.containsKey(txHashCode)) {
                        if(transaction.isConfirmed()) {
                            transactionList.put(txHashCode, transaction);
                            confirmedBalance = confirmedBalance.add(transaction.amount);
                        } else {
                            unconfirmedBalance = unconfirmedBalance.add(transaction.amount);
                        }
                    }
                }
            }

            private BigDecimal confirmedBalance = BigDecimal.ZERO;
            private BigDecimal unconfirmedBalance = BigDecimal.ZERO;
            private @NonNull String address;
        }

        private final Map<String, Address> addressList = new HashMap<String, Address>();

        private int confirmedTxCount = 0; // checkpoint
        public BigDecimal summaryConfirmedBalance() {
            BigDecimal confirmed = BigDecimal.ZERO, unconfirmed = BigDecimal.ZERO;
            synchronized (addressList) {
                for(Address address : addressList.values()) {
                    confirmed = confirmed.add(address.confirmedBalance);
                    unconfirmed = unconfirmed.add(address.unconfirmedBalance);
                }
                if(unconfirmed.compareTo(BigDecimal.ZERO) < 0) {
                    confirmed = confirmed.add(unconfirmed); // Lock withdrawal
                }
            }
            return confirmed;
        }
        public void loadAddresses(final JsonRPC jsonRPC) throws Exception {
            List<Object> args = new ArrayList<Object>();
            args.add(this.getName());
            List<String> addresses = jsonRPC.executeRpcRequest("getaddressesbyaccount", args, new TypeReference<JsonRPC.JsonRpcResponse<List<String>>>(){});
            synchronized (addressList) {
                for(String address : addresses) if(!addressList.containsKey(address)) {
                    addressList.put(address, new Address(address));
                }
            }
        }

        public void resetUnconfirmedBalance() {
            synchronized (addressList) {
                for(Address address : addressList.values()) {
                    address.setUnconfirmedBalance(BigDecimal.ZERO);
                }
            }
        }
        public void loadTransactions(final JsonRPC jsonRPC, int maxCount) throws Exception {
            List<Object> args = new ArrayList<Object>();
            args.add(this.getName());
            args.add(maxCount); // tx count
            args.add(confirmedTxCount); // tx from

            List<Account.Address.Transaction> transactions = jsonRPC.executeRpcRequest("listtransactions", args, new TypeReference<JsonRPC.JsonRpcResponse<List<Account.Address.Transaction>>>(){});

            synchronized (addressList) {
                Address address = null;
                for(Address.Transaction transaction : transactions) {
                    if(address == null || !address.getAddress().equals(transaction.address)) {
                        if(addressList.containsKey(transaction.address)) {
                            address = addressList.get(transaction.address);
                        } else {
                            address = new Address(transaction.address);
                            addressList.put(transaction.address, address);
                        }
                    }
                    if(!address.transactionList.containsKey(transaction.hashCode()) && transaction.isConfirmed()) {
                        confirmedTxCount++;
                    }
                    address.addTransaction(transaction);
                }
            }
        }

        public boolean importPrivateKey(final JsonRPC jsonRPC, final String privateKey) throws Exception {
            List<Object> args = new ArrayList<Object>();
            args.add(privateKey);
            args.add(this.getName());
            return jsonRPC.executeRpcRequest("importprivkey", args, new TypeReference<JsonRPC.JsonRpcResponse<Boolean>>() {});
        }

        public String generateNewAddress(final JsonRPC jsonRPC) throws Exception {
            List<Object> args = new ArrayList<Object>();
            args.add(this.getName());
            String response = jsonRPC.executeRpcRequest("getnewaddress", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
            synchronized (addressList) {
                addressList.put(response, new Address(response));
            }
            return response;
        }

        public String sendToAddress(final JsonRPC jsonRPC, final String address, final BigDecimal amount) throws Exception {
            List<Object> args = new ArrayList<Object>();
            args.add(this.getName());
            args.add(address);
            args.add(amount);
            return jsonRPC.executeRpcRequest("sendfrom", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
        }
    }

    public static Account generateAccount(final JsonRPC jsonRPC) throws Exception {
        String uuid = Account.generateUniqueAccountName();
        final Account account = new Account(uuid);
        account.generateNewAddress(jsonRPC);
        return account;
    }
}
