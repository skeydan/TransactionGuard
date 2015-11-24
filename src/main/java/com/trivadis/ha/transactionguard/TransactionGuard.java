package com.trivadis.ha.transactionguard;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

public class TransactionGuard {

    Properties props = new Properties();
    private String url;
    private String appUser;
    private String dbaUser;
    private String appPasswd;
    private String dbaPasswd;

    private final static Logger logger = Logger.getLogger(TransactionGuard.class.getName());

    private void getProperties() throws IOException {

        InputStream inputStream = Optional.ofNullable(getClass().getClassLoader().
                getResourceAsStream("config.properties")).orElseThrow(FileNotFoundException::new);
        props.load(inputStream);
        url = props.getProperty("url");
        appUser = props.getProperty("appUser");
        appPasswd = props.getProperty("appPasswd");
        dbaUser = props.getProperty("dbaUser");
        dbaPasswd = props.getProperty("dbaPasswd");
        inputStream.close();
    }

    private void updateSalaries(Connection conn) throws SQLException {
        String query = "select empno, sal from tg.emp";
        PreparedStatement stmt = conn.prepareStatement(query,
                ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int oldsal = rs.getInt("sal");
            int newsal = calculateNewValue(oldsal);
            logger.finest("Empno " + rs.getInt("empno") + ": prev sal = "
                    + oldsal + ", new sal = " + newsal);
            rs.updateInt("sal", newsal);
            rs.updateRow();
        }
        rs.close();
        stmt.close();
    }

    private void updateCommissions(Connection conn) throws SQLException {
        String query = "select empno, comm from tg.emp";
        PreparedStatement stmt = conn.prepareStatement(query, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int oldcomm = rs.getInt("comm");
            int newcomm = calculateNewValue(oldcomm);
            logger.finest("Empno " + rs.getInt("empno") + ": prev comm = " + oldcomm + ", new comm = " + newcomm);
            rs.updateInt("comm", newcomm);
            rs.updateRow();
        }
        rs.close();
        stmt.close();
    }

    private int calculateNewValue(int oldval) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warning("Interrupted!");
        }
        return oldval + 1;
    }

    private void createUser(Connection conn) throws SQLException {
        logger.finest("Recreating user " + appUser + ".");
        try {
            conn.createStatement().execute("drop user " + appUser + " cascade");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1918) {
                logger.finest("User " + appUser + " did not exist.");
            }
        }
        conn.createStatement().execute("create user " + appUser + " identified by " + appPasswd);
        conn.createStatement().execute("grant create session to " + appUser);
        conn.createStatement().execute("alter user " + appUser + " quota unlimited on users");
        conn.createStatement().execute("grant select any dictionary to " + appUser);
        conn.createStatement().execute("grant execute on sys.dbms_app_cont to " + appUser);
    }

    private void createTables(Connection conn) throws SQLException {
        logger.finest("Creating emp table.");
        conn.createStatement().execute("create table tg.emp as select * from scott.emp");
    }

    private void setup() throws SQLException, IOException {

        TGTransactionProcessor tp = new TGTransactionProcessor(url, dbaUser, dbaPasswd);
        if (tp.process(conn -> createUser(conn))) {
            logger.fine("User created.");
        }
        if (tp.process(conn -> createTables(conn))) {
            logger.fine("Tables created.");
        }

    }

    private void doWork() throws SQLException, IOException {

        TGTransactionProcessor tp = new TGTransactionProcessor(url, appUser, appPasswd);
        if (tp.process(conn -> updateSalaries(conn))) {
            logger.fine("Salaries updated.");
        }

        if (tp.process(conn -> updateCommissions(conn))) {
            logger.fine("Commissions updated.");
        }

    }

    public static void main(String[] args)
            throws SQLException, InterruptedException, IOException {

        TransactionGuard t = new TransactionGuard();
        t.getProperties();
        t.setup();
        t.doWork();
    }

}
