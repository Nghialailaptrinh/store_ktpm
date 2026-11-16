package vn.edu.sales.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.CustomerCommandLock;
import vn.edu.sales.application.port.out.IdempotencyStore;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.domain.exception.IdempotencyConflictException;
import vn.edu.sales.domain.model.IdempotencyOperation;
import vn.edu.sales.domain.model.IdempotencyRecord;
import vn.edu.sales.domain.model.Order;
import vn.edu.sales.domain.model.OrderItem;
import vn.edu.sales.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCommandServiceTest {

    private FakeIdempotencyStore idempotencyStore;
    private FakeCustomerCommandLock commandLock;
    private FakeOrderService orderService;
    private FakeCartService cartService;
    private OrderCommandService service;

    private final Order dummyOrder = new Order(
            100L,
            "ORD-001",
            1L,
            "Recipient",
            "0900000000",
            "Address",
            OrderStatus.PENDING,
            new BigDecimal("100.00"),
            LocalDateTime.now(),
            LocalDateTime.now(),
            List.of(new OrderItem(1L, 10L, "Product", new BigDecimal("100.00"), 1, new BigDecimal("100.00")))
    );

    @BeforeEach
    void setUp() {
        idempotencyStore = new FakeIdempotencyStore();
        commandLock = new FakeCustomerCommandLock();
        orderService = new FakeOrderService(dummyOrder);
        cartService = new FakeCartService(dummyOrder);
        TransactionRunner directRunner = new TransactionRunner() {
            @Override
            public <T> T execute(Supplier<T> work) {
                return work.get();
            }
        };
        service = new OrderCommandService(orderService, cartService, idempotencyStore, commandLock, directRunner, null);
    }

    @Test
    void createDirectLocksCustomerAndSavesRecord() {
        OrderCommandService.DirectOrderRequest req = new OrderCommandService.DirectOrderRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address",
                List.of(new OrderService.CreateLine(10L, 1))
        );

        Order result = service.createDirect(req, "test-key-1");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(commandLock.lockedUsers).contains(1L);
        assertThat(orderService.createCallCount.get()).isEqualTo(1);
        assertThat(idempotencyStore.records).hasSize(1);
        IdempotencyRecord saved = idempotencyStore.records.get(0);
        assertThat(saved.userId()).isEqualTo(1L);
        assertThat(saved.requestKey()).isEqualTo("test-key-1");
        assertThat(saved.orderId()).isEqualTo(100L);
    }

    @Test
    void createDirectWithSameKeyAndSameHashReturnsExistingOrderWithoutRecreating() {
        OrderCommandService.DirectOrderRequest req = new OrderCommandService.DirectOrderRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address",
                List.of(new OrderService.CreateLine(10L, 1))
        );

        Order first = service.createDirect(req, "test-key-1");
        assertThat(orderService.createCallCount.get()).isEqualTo(1);

        Order second = service.createDirect(req, "test-key-1");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(orderService.createCallCount.get()).isEqualTo(1); // not called again
    }

    @Test
    void createDirectWithSameKeyAndDifferentPayloadThrowsConflictException() {
        OrderCommandService.DirectOrderRequest req1 = new OrderCommandService.DirectOrderRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address 1",
                List.of(new OrderService.CreateLine(10L, 1))
        );
        service.createDirect(req1, "test-key-1");

        OrderCommandService.DirectOrderRequest req2 = new OrderCommandService.DirectOrderRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address 2",
                List.of(new OrderService.CreateLine(10L, 1))
        );

        assertThatThrownBy(() -> service.createDirect(req2, "test-key-1"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("test-key-1");

        assertThat(orderService.createCallCount.get()).isEqualTo(1);
    }

    @Test
    void checkoutCartLocksCustomerAndSavesRecord() {
        OrderCommandService.CartCheckoutRequest req = new OrderCommandService.CartCheckoutRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address"
        );

        Order result = service.checkoutCart(req, "cart-key-1");

        assertThat(result).isNotNull();
        assertThat(commandLock.lockedUsers).contains(1L);
        assertThat(cartService.checkoutCallCount.get()).isEqualTo(1);
        assertThat(idempotencyStore.records).hasSize(1);
    }

    @Test
    void invalidKeyThrowsIllegalArgumentException() {
        OrderCommandService.DirectOrderRequest req = new OrderCommandService.DirectOrderRequest(
                1L, "customer@example.test", "Recipient", "0900000000", "Address",
                List.of(new OrderService.CreateLine(10L, 1))
        );

        assertThatThrownBy(() -> service.createDirect(req, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createDirect(req, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createDirect(req, "a".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class FakeIdempotencyStore implements IdempotencyStore {
        final List<IdempotencyRecord> records = new ArrayList<>();

        @Override
        public Optional<IdempotencyRecord> findCompleted(Long userId, IdempotencyOperation operation, String key) {
            return records.stream()
                    .filter(r -> r.userId().equals(userId) && r.operation() == operation && r.requestKey().equals(key))
                    .findFirst();
        }

        @Override
        public void saveCompleted(IdempotencyRecord record) {
            records.add(record);
        }
    }

    private static class FakeCustomerCommandLock implements CustomerCommandLock {
        final List<Long> lockedUsers = new ArrayList<>();

        @Override
        public void lock(Long userId) {
            lockedUsers.add(userId);
        }
    }

    private static class FakeOrderService extends OrderService {
        final AtomicInteger createCallCount = new AtomicInteger();
        final Order dummyOrder;

        FakeOrderService(Order dummyOrder) {
            super(null, null, null, null, null, null);
            this.dummyOrder = dummyOrder;
        }

        @Override
        public Order create(String customerEmail, String recipientName, String recipientPhone,
                            String shippingAddress, List<CreateLine> lines) {
            createCallCount.incrementAndGet();
            return dummyOrder;
        }

        @Override
        public Order getAdminById(Long id) {
            return dummyOrder;
        }
    }

    private static class FakeCartService extends CartService {
        final AtomicInteger checkoutCallCount = new AtomicInteger();
        final Order dummyOrder;

        FakeCartService(Order dummyOrder) {
            super(null, null, null, null, null);
            this.dummyOrder = dummyOrder;
        }

        @Override
        public Order checkout(String email, String recipientName, String recipientPhone, String shippingAddress) {
            checkoutCallCount.incrementAndGet();
            return dummyOrder;
        }
    }
}
