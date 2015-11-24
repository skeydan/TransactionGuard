package com.trivadis.ha.transactionguard;

import java.sql.SQLException;

interface TransactionProcessor {
    boolean process(Transaction transaction) throws SQLException;
}
