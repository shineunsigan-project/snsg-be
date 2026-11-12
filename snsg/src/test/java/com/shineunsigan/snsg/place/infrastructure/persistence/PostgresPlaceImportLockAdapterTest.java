package com.shineunsigan.snsg.place.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 적재 잠금이 다른 연결과 실제로 경쟁하는지, 작업이 끝나면(예외가 나도) 풀리는지 실제 PostgreSQL에서 본다.
 * 세션 잠금이라 테스트 트랜잭션을 끄고, 다른 실행 대신 테스트가 별도 연결로 같은 키를 잡는다.
 * 동시에 쓰는 연결은 둘이다(테스트가 쥔 연결, adapter가 잡는 연결).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresPlaceImportLockAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresPlaceImportLockAdapterTest {

    @Autowired
    private PostgresPlaceImportLockAdapter lockAdapter;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("다른 연결이 잠금을 쥐고 있으면 작업을 돌리지 않고, 그 연결이 풀면 잡아서 돌린다")
    void lockedByOtherConnection() throws SQLException {
        AtomicBoolean ran = new AtomicBoolean();
        try (Connection other = dataSource.getConnection()) {
            assertThat(tryLock(other)).isTrue();

            assertThat(lockAdapter.runExclusively(() -> ran.set(true))).isFalse();
            assertThat(ran).isFalse();

            unlock(other);
        }

        assertThat(lockAdapter.runExclusively(() -> ran.set(true))).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("작업이 도는 동안에는 다른 연결이 잠금을 잡지 못하고, 끝나면 잡을 수 있다")
    void heldWhileRunning() throws SQLException {
        try (Connection other = dataSource.getConnection()) {
            AtomicBoolean otherGotLock = new AtomicBoolean(true);
            lockAdapter.runExclusively(() -> otherGotLock.set(tryLockUnchecked(other)));
            assertThat(otherGotLock).isFalse();

            assertThat(tryLock(other)).isTrue();
            unlock(other);
        }
    }

    @Test
    @DisplayName("작업이 예외를 던져도 끝난 뒤 다른 연결이 잠금을 잡을 수 있다")
    void releasedAfterException() throws SQLException {
        assertThatThrownBy(() -> lockAdapter.runExclusively(() -> {
            throw new IllegalStateException("적재 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("적재 실패");

        try (Connection other = dataSource.getConnection()) {
            assertThat(tryLock(other)).isTrue();
            unlock(other);
        }
    }

    @Test
    @DisplayName("작업이 예외를 던진 뒤 잠금 해제까지 실패하면 작업의 예외가 그대로 나가고 해제 실패는 거기에 덧붙는다")
    void taskFailureIsKeptWhenUnlockFails() throws SQLException {
        Connection connection = mock(Connection.class);
        DataSource failingDataSource = mock(DataSource.class);
        when(failingDataSource.getConnection()).thenReturn(connection);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        ResultSet locked = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(lockStatement);
        when(lockStatement.executeQuery()).thenReturn(locked);
        when(locked.getBoolean(1)).thenReturn(true);
        SQLException unlockFailure = new SQLException("연결 끊김");
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenThrow(unlockFailure);
        PostgresPlaceImportLockAdapter adapter = new PostgresPlaceImportLockAdapter(failingDataSource);

        assertThatThrownBy(() -> adapter.runExclusively(() -> {
            throw new IllegalStateException("적재 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("적재 실패")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(unlockFailure));
    }

    private boolean tryLockUnchecked(Connection connection) {
        try {
            return tryLock(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean tryLock(Connection connection) throws SQLException {
        return query(connection, "SELECT pg_try_advisory_lock(?)");
    }

    private void unlock(Connection connection) throws SQLException {
        assertThat(query(connection, "SELECT pg_advisory_unlock(?)")).isTrue();
    }

    private boolean query(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, PostgresPlaceImportLockAdapter.LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }
}
