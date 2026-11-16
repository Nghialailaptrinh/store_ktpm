package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.IdempotencyOperation;
import vn.edu.sales.domain.model.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> findCompleted(Long userId, IdempotencyOperation operation, String key);
    void saveCompleted(IdempotencyRecord record);
}
