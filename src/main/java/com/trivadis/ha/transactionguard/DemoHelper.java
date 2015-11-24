package com.trivadis.ha.transactionguard;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import oracle.jdbc.LogicalTransactionId;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;

public class DemoHelper {

    private final static Logger logger = Logger.getLogger(DemoHelper.class.getName());

    private static final String GET_LTXID_OUTCOME = "declare"
            + "  l_commit         boolean;"
            + "  l_call_completed boolean;"
            + "begin"
            + "  dbms_app_cont.get_ltxid_outcome(:1, l_commit, l_call_completed);"
            + "  :2 := case when l_commit         then 1 else 0 end;"
            + "  :3 := case when l_call_completed then 1 else 0 end;"
            + "end;";
    
    private static boolean isLTxIdCommitted(LogicalTransactionId ltxid, Connection conn)
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
    
    protected static void logSessionInfo(Connection conn, String serviceName, String moduleName, String program) throws SQLException {

        PreparedStatement stmt = conn.prepareStatement("select sid, serial#, inst_id, module, program"
                + " from gv$session where service_name=? and module=? and program=?");
       
        stmt.setString(1, serviceName);
        stmt.setString(2, moduleName);
        stmt.setString(3, program);

        ResultSet rset = stmt.executeQuery();
        rset.next();
        int sid = rset.getInt("sid");
        int serial = rset.getInt("serial#");
        int instId = rset.getInt("inst_id");

        if (rset.next()) {
            throw new IllegalStateException("There should be at most one session running at this stage!");

        }
        rset.close();
        stmt.close();
        String killMe = new StringBuilder("Got a connection. You may kill this session as follows:\nalter system kill session '").
                append(sid).append(",").append(serial).append(",@").append(instId).append("';").toString();
        logger.info(killMe);

    }
    
    protected static void ORA_14906(Connection conn) throws SQLException {
        
        LogicalTransactionId ltxid = ((OracleConnection) conn).getLogicalTransactionId();
        boolean done = isLTxIdCommitted(ltxid, conn);
        
    }
    
}
