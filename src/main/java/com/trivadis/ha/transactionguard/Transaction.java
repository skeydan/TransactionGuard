package com.trivadis.ha.transactionguard;

import java.sql.Connection;  
import java.sql.SQLException;

@FunctionalInterface
public interface Transaction {  
    public void execute(Connection connection) throws SQLException;
}

