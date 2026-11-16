package vn.edu.sales.infrastructure.persistence.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.sales.application.port.out.CustomerCommandLock;
import vn.edu.sales.domain.exception.ResourceNotFoundException;

import java.util.List;

@Repository
public class JdbcCustomerCommandLock implements CustomerCommandLock {
    private final JdbcTemplate jdbc;

    public JdbcCustomerCommandLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock(Long userId) {
        List<Long> rows = jdbc.query(
                "SELECT id FROM users WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khách hàng: " + userId);
        }
    }
}
