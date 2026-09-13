package com.worthly.sync.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConnectionLock {

    private final JdbcTemplate jdbcTemplate;

    public ConnectionLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryWithLock(UUID connectionId, Runnable work) {
        Boolean acquired = jdbcTemplate.execute((Connection conn) -> {
            if (!tryLock(conn, connectionId)) {
                return false;
            }
            try {
                work.run();
                return true;
            } finally {
                unlock(conn, connectionId);
            }
        });
        return Boolean.TRUE.equals(acquired);
    }

    private static boolean tryLock(Connection conn, UUID connectionId) throws java.sql.SQLException {
        try (PreparedStatement statement = conn.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockKey(connectionId));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection conn, UUID connectionId) throws java.sql.SQLException {
        try (PreparedStatement statement = conn.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, lockKey(connectionId));
            statement.executeQuery().close();
        }
    }

    static long lockKey(UUID connectionId) {
        return connectionId.getMostSignificantBits() ^ connectionId.getLeastSignificantBits();
    }
}
