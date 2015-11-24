package com.trivadis.ha.transactionguard;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import oracle.jdbc.OracleConnection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.util.Formatter;
import java.util.Properties;
import java.util.logging.*;
import oracle.jdbc.LogicalTransactionId;
import oracle.jdbc.OracleTypes;

public class TGTransactionProcessor extends AbstractTransactionProcessor {

    private final String url;
    private final String program = "JDBC Thin Client";

    private static final int MAXRETRIES = 5;
    private final Properties userProps = new Properties();
    private final static Logger logger = Logger.getLogger(TGTransactionProcessor.class.getName());

    private static final String GET_LTXID_OUTCOME = "declare"
            + "  l_commit         boolean;"
            + "  l_call_completed boolean;"
            + "begin"
            + "  dbms_app_cont.get_ltxid_outcome(:1, l_commit, l_call_completed);"
            + "  :2 := case when l_commit         then 1 else 0 end;"
            + "  :3 := case when l_call_completed then 1 else 0 end;"
            + "end;";

    TGTransactionProcessor(String url, String user, String passwd) throws IOException {

        getProperties();
        this.url = url;
        userProps.setProperty("user", user);
        userProps.setProperty("password", passwd);
        userProps.setProperty("autoCommit", "false");
    }

    @Override
    protected Connection getConnection() throws SQLException {

        Connection conn = null;
        while (conn == null) {
            try {
                conn = DriverManager.getConnection(url, userProps);
            } catch (SQLException r) {
                logger.severe("Could not connect: " + r.getMessage());
            }
        }
        setModule(conn, moduleName);
        DemoHelper.logSessionInfo(conn, serviceName, moduleName, program);
        return conn;
    }

    private boolean isLTxIdCommitted(LogicalTransactionId ltxid, Connection conn)
            throws SQLException {
        CallableStatement cstmt = null;

        cstmt = conn.prepareCall(GET_LTXID_OUTCOME);
        cstmt.setObject(1, ltxid);
        cstmt.registerOutParameter(2, OracleTypes.BIT);
        cstmt.registerOutParameter(3, OracleTypes.BIT);
        cstmt.execute();
        boolean committed = cstmt.getBoolean(2);
        boolean callCompleted = cstmt.getBoolean(3);
        logger.info("call completed: " + callCompleted + ", committed: " + committed);
        cstmt.close();

        return committed;
    }

    private static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        try (Formatter formatter = new Formatter(sb)) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean process(Transaction transaction) throws SQLException {

        boolean done = false;
        int tries = 0;
        Connection conn = getConnection();

        while (!done && tries <= MAXRETRIES) {

            try {

                logger.info("Starting transaction.");

                transaction.execute(conn);

                if (ORA_14906) {
                    DemoHelper.ORA_14906(conn);
                }

                logger.info("Work done. Now going to commit!");
                conn.commit();
                conn.close();
                done = true;

            } catch (SQLRecoverableException e) {

                try {
                    conn.close();
                } catch (Exception ex) {
                }
                LogicalTransactionId ltxid = ((OracleConnection) conn).getLogicalTransactionId();
                logger.info("transaction failed: ltxid is: " + byteArrayToHexString(ltxid.getBytes()));
                logger.info("Exception was: " + e.getMessage());

                Connection newconn = getConnection();
                setModule(newconn, moduleName);
                done = isLTxIdCommitted(ltxid, newconn);
                if (done) {
                    logger.info("Failed transaction had already been committed.");
                } else {
                    logger.info("Replay of transaction neccessary.");
                    tries++;
                    conn = newconn;
                }

            }
        }
        return true;
    }

    private void setModule(Connection conn, String module_name) throws SQLException {

        conn.setClientInfo("OCSID.MODULE", module_name);

    }

}
