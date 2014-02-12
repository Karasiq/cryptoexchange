package com.springapp.cryptoexchange.utils;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.anotheria.idbasedlock.IdBasedLock;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class LockManager {
    IdBasedLockManager<Long> currencyLockManager = new SafeIdBasedLockManager<>();

    @RequiredArgsConstructor
    public static class LockStack<T>  {
        private final IdBasedLockManager<T> lockManager;
        private final Map<T, IdBasedLock<T>> lockMap = new HashMap<>();
        public synchronized void push(T key) {
            if(!lockMap.containsKey(key)) {
                IdBasedLock<T> lock = lockManager.obtainLock(key);
                lock.lock();
                lockMap.put(key, lock);
            }
        }
        public synchronized void pop(T key) {
            IdBasedLock<T> lock = lockMap.get(key);
            lockMap.remove(key);
            lock.unlock();
        }
        public synchronized void flush() {
            for(IdBasedLock<T> lock : lockMap.values()) {
                lock.unlock();
            }
            lockMap.clear();
        }
    }
}
