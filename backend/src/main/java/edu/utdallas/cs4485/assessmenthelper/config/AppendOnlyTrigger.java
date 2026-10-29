package edu.utdallas.cs4485.assessmenthelper.config;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

public class AppendOnlyTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("This table is append-only: its rows cannot be changed or deleted.");
    }
}
