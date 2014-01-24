package com.bitcoin.daemon;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.type.TypeReference;

import java.math.BigDecimal;
import java.util.*;

public class CryptoCoinWallet {
    private CryptoCoinWallet() {
        // no instances
    }
    public static final int REQUIRED_CONFIRMATIONS = 6;

    public
    @Data
    static class Account {
        public static String generateUniqueAccountName() {
            return UUID.randomUUID().toString();
        }

        private @NonNull String name;

        @Data public static class Address {
            @Data
            private static class TransactionDetails {
                protected String account;
                protected String address;
                protected BigDecimal amount = BigDecimal.ZERO;
                protected BigDecimal fee = BigDecimal.ZERO;
                protected String category; // send/receive
            }

            @Data
            @EqualsAndHashCode(exclude = {"details", "confirmations"}, callSuper = false)
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Transaction extends TransactionDetails {
                protected long time;
                protected String txid;
                protected int confirmations;
                protected List<TransactionDetails> details;

                public boolean isConfirmed() {
                    return confirmations >= REQUIRED_CONFIRMATIONS;
                }
            }
            private final HashMap<Integer, Transaction> transactionList = new HashMap<>();
            public void addTransaction(Transaction transaction) {
                synchronized (transactionList) {
                    int txHashCode = transaction.hashCode();
                    if(!transactionList.containsKey(txHashCode)) {
                        BigDecimal amount = transaction.getAmount(), fee = transaction.getFee();
                        if(transaction.isConfirmed()) {
                            transactionList.put(txHashCode, transaction);
                            confirmedBalance = confirmedBalance.add(amount).add(fee);
                        } else {
                            if(amount.compareTo(BigDecimal.ZERO) < 0) { // Withdraw
                                unconfirmedWithdraw = unconfirmedWithdraw.add(amount).add(fee);
                            } else {
                                unconfirmedBalance = unconfirmedBalance.add(amount).add(fee);
                            }
                        }
                    }
                }
            }
            public void resetUnconfirmed() {
                setUnconfirmedBalance(BigDecimal.ZERO);
                setUnconfirmedWithdraw(BigDecimal.ZERO);
            }

            private BigDecimal confirmedBalance = BigDecimal.ZERO;
            private BigDecimal unconfirmedBalance = BigDecimal.ZERO;
            private BigDecimal unconfirmedWithdraw = BigDecimal.ZERO;
            private @NonNull String address;
        }

        private final Map<String, Address> addressList = new HashMap<>();

        private int confirmedTxCount = 0; // checkpoint
        public BigDecimal summaryConfirmedBalance() {
            BigDecimal confirmed = BigDecimal.ZERO, pendingWithdraw = BigDecimal.ZERO;
            synchronized (addressList) {
                for(Address address : addressList.values()) {
                    confirmed = confirmed.add(address.getConfirmedBalance());
                    pendingWithdraw = pendingWithdraw.add(address.getUnconfirmedWithdraw());
                }
            }
            return confirmed.add(pendingWithdraw);
        }

        public BigDecimal summaryConfirmedBalance(final Set<String> addresses) {
            BigDecimal confirmed = BigDecimal.ZERO, pendingWithdraw = BigDecimal.ZERO;
            synchronized (addressList) {
                for(Address address : addressList.values()) if(addresses.contains(address.getAddress())) {
                    confirmed = confirmed.add(address.getConfirmedBalance());
                    pendingWithdraw = pendingWithdraw.add(address.getUnconfirmedWithdraw());
                }
            }
            return confirmed.add(pendingWithdraw);
        }

        public void loadAddresses(final JsonRPC jsonRPC) throws Exception {
            List<Object> args = new ArrayList<>();
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
                    address.resetUnconfirmed();
                }
            }
        }
        public void loadTransactions(final JsonRPC jsonRPC, int maxCount) throws Exception {
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            args.add(maxCount); // tx count
            args.add(confirmedTxCount); // tx from

            List<Account.Address.Transaction> transactions = jsonRPC.executeRpcRequest("listtransactions", args, new TypeReference<JsonRPC.JsonRpcResponse<List<Account.Address.Transaction>>>(){});

            synchronized (addressList) {
                resetUnconfirmedBalance();
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
            List<Object> args = new ArrayList<>();
            args.add(privateKey);
            args.add(this.getName());
            return jsonRPC.executeRpcRequest("importprivkey", args, new TypeReference<JsonRPC.JsonRpcResponse<Boolean>>() {});
        }

        public Address getRandomAddress() {
            synchronized (addressList) {
                for(Address address : addressList.values()) {
                    return address;
                }
            }
            return null;
        }

        public Address generateNewAddress(final JsonRPC jsonRPC) throws Exception {
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            String response = jsonRPC.executeRpcRequest("getnewaddress", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
            Address address = new Address(response);
            synchronized (addressList) {
                addressList.put(response, address);
            }
            return address;
        }

        public Address.Transaction sendToAddress(final JsonRPC jsonRPC, final String address, final BigDecimal amount) throws Exception {
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            args.add(address);
            args.add(amount);
            synchronized (addressList) {
                String txid = jsonRPC.executeRpcRequest("sendfrom", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
                loadTransactions(jsonRPC, 20); // Refresh balance
                args.clear();
                args.add(txid);
                return jsonRPC.executeRpcRequest("gettransaction", args, new TypeReference<JsonRPC.JsonRpcResponse<Address.Transaction>>(){});
            }
        }
    }

    public static Account generateAccount(final JsonRPC jsonRPC, String prefix) throws Exception {
        final String uuid = String.format("%s-%s", prefix, Account.generateUniqueAccountName());
        final Account account = new Account(uuid);
        account.generateNewAddress(jsonRPC);
        return account;
    }

    public static Account getDefaultAccount() {
        return new Account("exchange-default");
    }
}
