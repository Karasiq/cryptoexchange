package com.bitcoin.daemon;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.*;

public class CryptoCoinWallet {
    public
    @Data
    static class Account implements AbstractWallet {
        public static String generateUniqueAccountName() {
            return UUID.randomUUID().toString();
        }

        private @NonNull JsonRPC jsonRPC;
        private @NonNull String name;
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

        public BigDecimal summaryConfirmedBalance(final Set<Object> addresses) {
            BigDecimal confirmed = BigDecimal.ZERO, pendingWithdraw = BigDecimal.ZERO;
            synchronized (addressList) {
                for(Address address : addressList.values()) if(addresses.contains(address.getAddress())) {
                    confirmed = confirmed.add(address.getConfirmedBalance());
                    pendingWithdraw = pendingWithdraw.add(address.getUnconfirmedWithdraw());
                }
            }
            return confirmed.add(pendingWithdraw);
        }

        public void loadAddresses() throws Exception {
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
        public void loadTransactions(int maxCount) throws Exception {
            loadAddresses();
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            args.add(maxCount); // tx count
            args.add(confirmedTxCount); // tx from

            List<Address.Transaction> transactions = jsonRPC.executeRpcRequest("listtransactions", args, new TypeReference<JsonRPC.JsonRpcResponse<List<Address.Transaction>>>(){});

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
                    if(!address.getTransactionList().containsKey(transaction.hashCode()) && transaction.isConfirmed()) {
                        confirmedTxCount++;
                    }
                    address.addTransaction(transaction);
                }
            }
        }

        public boolean importPrivateKey(final String privateKey) throws Exception {
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

        public Address generateNewAddress() throws Exception {
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            String response = jsonRPC.executeRpcRequest("getnewaddress", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
            Address address = new Address(response);
            synchronized (addressList) {
                addressList.put(response, address);
            }
            return address;
        }

        public Address.Transaction sendToAddress(final String address, final BigDecimal amount) throws Exception {
            List<Object> args = new ArrayList<>();
            args.add(this.getName());
            args.add(address);
            args.add(amount);
            synchronized (addressList) {
                String txid = jsonRPC.executeRpcRequest("sendfrom", args, new TypeReference<JsonRPC.JsonRpcResponse<String>>(){});
                loadTransactions(20); // Refresh balance
                args.clear();
                args.add(txid);
                return jsonRPC.executeRpcRequest("gettransaction", args, new TypeReference<JsonRPC.JsonRpcResponse<Address.Transaction>>(){});
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
