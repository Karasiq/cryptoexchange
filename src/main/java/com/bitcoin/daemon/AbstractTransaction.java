package com.bitcoin.daemon;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;

public interface AbstractTransaction {
    Object getAddress();
    Object getTxid();
    Date getTime();
    BigDecimal getFee();
    BigDecimal getAmount();

    public static final Comparator<AbstractTransaction> TIME_COMPARATOR = new Comparator<AbstractTransaction>() {
        @Override
        public int compare(AbstractTransaction o1, AbstractTransaction o2) {
            return o1.getTime().compareTo(o2.getTime());
        }
    };
}
