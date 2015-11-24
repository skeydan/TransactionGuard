package com.trivadis.ha.transactionguard;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

public abstract class AbstractTransactionProcessor implements TransactionProcessor {

    Properties props = new Properties();
    protected String moduleName;
    protected String serviceName;
    protected String appUser;
    protected boolean ORA_14906;

    protected void getProperties() throws IOException {

        InputStream inputStream = Optional.ofNullable(getClass().getClassLoader().
                getResourceAsStream("config.properties")).orElseThrow(FileNotFoundException::new);
        props.load(inputStream);
        serviceName = props.getProperty("serviceName");
        appUser = props.getProperty("appUser");
        moduleName = props.getProperty("moduleName");
        ORA_14906 = Boolean.parseBoolean(props.getProperty("ORA_14906"));
        inputStream.close();
    }

    protected abstract Connection getConnection() throws SQLException;

}
