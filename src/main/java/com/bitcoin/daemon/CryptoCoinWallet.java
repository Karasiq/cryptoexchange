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
import java.util.concurrent.*;

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
        final Map<String, Address> addressList = new HashMap<>();

        public BigDecimal summaryConfirmedBalance() throws Exception {
            return jsonRPC.executeRpcRequest("getreceivedbyaccount", Arrays.asList(getName(), Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<BigDecimal>>(){});
        }

        public BigDecimal getReceivedByAddress(String address) throws Exception {
            Assert.hasLength(address, "Address can not be empty");
            return jsonRPC.executeRpcRequest("getreceivedbyaddress", Arrays.asList(address, Settings.REQUIRED_CONFIRMATIONS), new TypeReference<JsonRPC.Response<BigDecimal>>(){});
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
        public List<Address.Transaction> getTransactions(final Set<?> addresses) throws Exception {
            final List<Address.Transaction> transactionList = new ArrayList<>(5);
            if (addresses.size() < 1) {
                return transactionList;
            }
            synchronized (addressList) {
                for(Object strAddress : addresses) if(strAddress instanceof String && addressList.containsKey(strAddress)) {
                    transactionList.addAll(addressList.get(strAddress).getTransactionList().values());
                }
            }
            return transactionList;
        }

        public Address.Transaction getTransaction(String transactionId) throws Exception {
            return jsonRPC.executeRpcRequest("gettransaction", Arrays.asList(transactionId), new TypeReference<JsonRPC.Response<Address.Transaction>>(){});
        }

        @SuppressWarnings("unchecked")
        public BigDecimal summaryConfirmedBalance(final Set<?> addresses) throws Exception {
            BigDecimal confirmed = BigDecimal.ZERO;
            if (addresses.size() < 1) {
                return confirmed; // 0
            }

            List<Future<BigDecimal>> futureList = new ArrayList<>(addresses.size());
            ExecutorService executorService = Executors.newCachedThreadPool();
            for(final Object strAddress : addresses) {
                Assert.isInstanceOf(String.class, strAddress);
                futureList.add(executorService.submit(new Callable<BigDecimal>() {
                    @Override
                    public BigDecimal call() throws Exception {
                        return getReceivedByAddress((String) strAddress);
                    }
                }));
            }

            for(Future<BigDecimal> future : futureList) try {
                confirmed = confirmed.add(future.get());
            } catch (ExecutionException e) {
                throw e.getCause() != null ? (Exception) e.getCause() : e;
            } finally {
                executorService.shutdown();
            }


            return confirmed;
        }

        public Set<?> getAddressSet() throws Exception {
            return jsonRPC.executeRpcRequest("getaddressesbyaccount", Arrays.asList(this.getName()), new TypeReference<JsonRPC.Response<Set<String>>>(){});
        }

        public void loadTransactions(int maxCount) throws Exception { // Expensive
            // loadAddresses();
            List<Address.Transaction> transactions = jsonRPC.executeRpcRequest("listtransactions", Arrays.asList(this.getName(), maxCount), new TypeReference<JsonRPC.Response<List<Address.Transaction>>>(){});

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
            return jsonRPC.executeRpcRequest("importprivkey", Arrays.asList(privateKey, this.getName()), new TypeReference<JsonRPC.Response<Boolean>>() {});
        }

        public Address getRandomAddress() {
            synchronized (addressList) {
                Iterator<Address> iterator = addressList.values().iterator();
                return iterator.hasNext() ? iterator.next() : null;
            }
        }

        public Address generateNewAddress() throws Exception {
            String response = jsonRPC.executeRpcRequest("getnewaddress", Arrays.asList(this.getName()), new TypeReference<JsonRPC.Response<String>>(){});
            Address address = new Address(response);
            synchronized (addressList) {
                addressList.put(response, address);
            }
            return address;
        }

        public Address.Transaction sendToAddress(String address, @NonNull BigDecimal amount) throws Exception {
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
    }

    public static List<Account> getAccountList(JsonRPC jsonRPC) throws Exception {
        final Map<String, BigDecimal> result = jsonRPC.executeRpcRequest("listaccounts", null, new TypeReference<JsonRPC.Response<Map<String, BigDecimal>>>(){});
        final List<Account> accountList = new ArrayList<>(result.size());
        for(String accountName : result.keySet()) {
            accountList.add(getAccount(jsonRPC, accountName));
        }
        return accountList;
    }

    public static Account generateAccount(JsonRPC jsonRPC, String prefix) throws Exception {
        final String uuid = String.format("%s-%s", prefix, Account.generateUniqueAccountName());
        final Account account = getAccount(jsonRPC, uuid);
        account.generateNewAddress();
        return account;
    }

    public static Account getDefaultAccount(JsonRPC jsonRPC) {
        return getAccount(jsonRPC, Settings.DEFAULT_ACCOUNT);
    }

    public static Account getAccount(JsonRPC jsonRPC, String accountName) {
        return new Account(jsonRPC, accountName);
    }
}
