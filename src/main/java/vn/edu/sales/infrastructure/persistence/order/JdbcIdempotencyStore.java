package vn.edu.sales.infrastructure.persistence.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.IdempotencyStore;
import vn.edu.sales.domain.model.IdempotencyOperation;
import vn.edu.sales.domain.model.IdempotencyRecord;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {
    private final JdbcTemplate jdbc;

    private static final RowMapper<IdempotencyRecord> MAPPER = (rs, rowNum) -> {
        String opStr = rs.getString("operation");
        IdempotencyOperation op = null;
        if (opStr != null) {
            try {
                op = IdempotencyOperation.valueOf(opStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;
        return new IdempotencyRecord(
                rs.getLong("user_id"),
                op,
                rs.getString("request_key"),
                rs.getString("request_hash"),
                rs.getLong("order_id"),
                createdAt
        );
    };

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findCompleted(Long userId, IdempotencyOperation operation, String key) {
        String sql = "SELECT user_id, operation, request_key, request_hash, order_id, created_at " +
                "FROM order_idempotency WHERE user_id = ? AND operation = ? AND request_key = ?";
        List<IdempotencyRecord> results = jdbc.query(sql, MAPPER, userId, operation.name(), key);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void saveCompleted(IdempotencyRecord record) {
        String sql = "INSERT INTO order_idempotency (user_id, operation, request_key, request_hash, order_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String opName = record.operation() != null ? record.operation().name() : IdempotencyOperation.CREATE_ORDER.name();
        Timestamp createdAt = record.createdAt() != null ? Timestamp.valueOf(record.createdAt()) : Timestamp.valueOf(LocalDateTime.now());
        jdbc.update(sql, record.userId(), opName, record.requestKey(), record.requestHash(), record.orderId(), createdAt);
    }
}
