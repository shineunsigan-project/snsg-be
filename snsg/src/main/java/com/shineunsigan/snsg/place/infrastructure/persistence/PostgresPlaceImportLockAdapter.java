package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.place.application.port.out.PlaceImportLockPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL 세션 advisory lock으로 장소 적재를 한 곳에서만 돌린다.
 * <p>
 * 트랜잭션 잠금(pg_advisory_xact_lock)은 실행 전체를 트랜잭션 하나로 감싸야 해서, 원천 호출을 트랜잭션 밖에 두는 규칙과 맞지 않는다.
 * 세션 잠금은 잡은 연결에서 풀어야 하므로 실행하는 동안 연결 하나를 쥐고 있고, 반영은 다른 연결의 트랜잭션에서 한다.
 * 서버가 죽어 연결이 끊기면 PostgreSQL이 잠금을 푼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresPlaceImportLockAdapter implements PlaceImportLockPort {

    /**
     * 장소 적재 잠금 키. 일정의 사용자 잠금(hashtext, int 범위)과 겹치지 않게 int 범위 밖의 값을 쓴다.
     * "place"의 ASCII 바이트다.
     */
    static final long LOCK_KEY = 0x706C616365L;

    private final DataSource dataSource;

    @Override
    public boolean runExclusively(Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                return false;
            }
            try {
                task.run();
            } catch (RuntimeException | Error taskFailure) {
                unlockAfterFailure(connection, taskFailure);
                throw taskFailure;
            }
            unlock(connection);
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("장소 적재 잠금을 다루지 못했다", exception);
        }
    }

    private boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    /** 작업이 실패한 뒤 풀다 실패하면 작업의 예외를 살리고, 풀지 못한 예외는 거기에 덧붙인다. */
    private void unlockAfterFailure(Connection connection, Throwable taskFailure) {
        try {
            unlock(connection);
        } catch (SQLException unlockFailure) {
            taskFailure.addSuppressed(unlockFailure);
        }
    }

    /** 풀지 못하면 연결을 풀에 돌려주지 않고 끊는다. 잠금을 쥔 연결이 풀에 남으면 다음 실행이 막힌다. */
    private void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        } catch (SQLException exception) {
            log.warn("장소 적재 잠금을 풀지 못해 연결을 끊는다", exception);
            connection.abort(Runnable::run);
            throw exception;
        }
    }
}
