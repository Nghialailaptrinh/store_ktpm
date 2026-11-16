package vn.edu.sales.application.service;

import vn.edu.sales.application.port.out.CustomerCommandLock;
import vn.edu.sales.application.port.out.IdempotencyStore;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.model.IdempotencyOperation;
import vn.edu.sales.domain.model.IdempotencyRecord;
import vn.edu.sales.domain.model.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class OrderCommandService {
    private final OrderService orders;
    private final CartService carts;
    private final IdempotencyStore idempotency;
    private final CustomerCommandLock locks;
    private final TransactionRunner transactions;
    private final UserRepository users;
    private final OrderRequestFingerprint fingerprint;

    public OrderCommandService(OrderService orders, CartService carts,
                               IdempotencyStore idempotency, CustomerCommandLock locks,
                               TransactionRunner transactions) {
        this(orders, carts, idempotency, locks, transactions, null);
    }

    public OrderCommandService(OrderService orders, CartService carts,
                               IdempotencyStore idempotency, CustomerCommandLock locks,
                               TransactionRunner transactions, UserRepository users) {
        this.orders = orders;
        this.carts = carts;
        this.idempotency = idempotency;
        this.locks = locks;
        this.transactions = transactions;
        this.users = users;
        this.fingerprint = new OrderRequestFingerprint();
    }

    public Order createDirect(DirectOrderRequest request, String key) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu đặt hàng không được để trống");
        }
        validateKey(key);
        String requestHash = fingerprint.forDirectOrder(request);
        Long userId = resolveUserId(request.userId(), request.customerEmail());
        return executeOnce(userId, IdempotencyOperation.CREATE_ORDER, key, requestHash, () ->
                orders.create(request.customerEmail(), request.recipientName(),
                        request.recipientPhone(), request.shippingAddress(), request.lines())
        );
    }

    public Order checkoutCart(CartCheckoutRequest request, String key) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu thanh toán giỏ hàng không được để trống");
        }
        validateKey(key);
        String requestHash = fingerprint.forCartCheckout(request);
        Long userId = resolveUserId(request.userId(), request.customerEmail());
        return executeOnce(userId, IdempotencyOperation.CHECKOUT_CART, key, requestHash, () ->
                carts.checkout(request.customerEmail(), request.recipientName(),
                        request.recipientPhone(), request.shippingAddress())
        );
    }

    private Order executeOnce(Long userId, IdempotencyOperation operation, String key,
                               String requestHash, Supplier<Order> command) {
        return transactions.execute(() -> {
            locks.lock(userId);
            var completed = idempotency.findCompleted(userId, operation, key);
            if (completed.isPresent()) {
                IdempotencyRecord record = completed.get();
                record.ensureSameRequest(requestHash);
                return orders.getAdminById(record.orderId());
            }

            Order order = command.get();
            IdempotencyRecord record = new IdempotencyRecord(
                    userId,
                    operation,
                    key,
                    requestHash,
                    order.id(),
                    LocalDateTime.now()
            );
            idempotency.saveCompleted(record);
            return order;
        });
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Khóa Idempotency-Key không được để trống");
        }
        if (key.length() > 120) {
            throw new IllegalArgumentException("Khóa Idempotency-Key quá dài (tối đa 120 ký tự)");
        }
    }

    private Long resolveUserId(Long userId, String email) {
        if (userId != null) {
            return userId;
        }
        if (users != null && email != null) {
            return users.findByEmail(email.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng: " + email))
                    .id();
        }
        throw new IllegalArgumentException("Không xác định được ID khách hàng");
    }

    public record DirectOrderRequest(
            Long userId,
            String customerEmail,
            String recipientName,
            String recipientPhone,
            String shippingAddress,
            List<OrderService.CreateLine> lines
    ) {
        public DirectOrderRequest(String customerEmail, String recipientName, String recipientPhone,
                                  String shippingAddress, List<OrderService.CreateLine> lines) {
            this(null, customerEmail, recipientName, recipientPhone, shippingAddress, lines);
        }
    }

    public record CartCheckoutRequest(
            Long userId,
            String customerEmail,
            String recipientName,
            String recipientPhone,
            String shippingAddress
    ) {
        public CartCheckoutRequest(String customerEmail, String recipientName,
                                   String recipientPhone, String shippingAddress) {
            this(null, customerEmail, recipientName, recipientPhone, shippingAddress);
        }
    }
}
