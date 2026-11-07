package com.briefwise.jobs.infrastructure.persistence;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.port.JobLock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A Postgres session-level advisory lock per job lock group ({@code pg_try_advisory_lock}): no table, works on Neon.
 * The lock belongs to one database session, so the connection is taken from the pool and held for the whole run, then
 * unlocked and returned. If the process dies, Postgres ends the session and the lock is released with it.
 */
@Component
class PostgresJobLock implements JobLock {

    private static final Logger log = LoggerFactory.getLogger(PostgresJobLock.class);

    /** Namespaced, so the key cannot collide with another advisory lock user of the same database. */
    private static final String KEY_PREFIX = "briefwise.job.";

    private final DataSource dataSource;

    PostgresJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Held> tryAcquire(JobName job) {
        String key = KEY_PREFIX + job.lockGroup();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (call(connection, "SELECT pg_try_advisory_lock(hashtext(?))", key)) {
                Connection held = connection;
                return Optional.of(() -> release(held, key));
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("Could not take the lock for job " + job.id(), e);
        }
    }

    private static void release(Connection connection, String key) {
        try {
            call(connection, "SELECT pg_advisory_unlock(hashtext(?))", key);
        } catch (SQLException e) {
            log.warn("Could not release the lock {}; it ends with the database session: {}", key, e.toString());
        } finally {
            closeQuietly(connection);
        }
    }

    private static boolean call(Connection connection, String sql, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.debug("Closing the lock connection failed: {}", e.toString());
        }
    }
}
