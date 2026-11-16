package vn.edu.sales.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.support.MemberAApiTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIdempotencyApiIT extends MemberAApiTestSupport {

    private String admin() throws Exception {
        createUser("idempotency-admin@example.test", Role.ADMIN);
        return login("idempotency-admin@example.test", ORIGINAL);
    }

    private long product(String adminToken, int stock) throws Exception {
        var result = mvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"IDEM-PROD-1","name":"Test Keyboard","price":100.00,"stockQuantity":%d}
                        """.formatted(stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return payload(result).path("id").asLong();
    }

    @Test
    void directOrderWithIdempotencyKeyIsIdempotent() throws Exception {
        String adminToken = admin();
        long productId = product(adminToken, 10);
        String customerToken = registerCustomer();

        String key = "direct-idemp-key-1";
        String body1 = """
                {
                    "recipientName": "Nguyen Van A",
                    "recipientPhone": "0987654321",
                    "shippingAddress": "123 Cau Giay, Ha Noi",
                    "items": [{"productId": %d, "quantity": 2}]
                }
                """.formatted(productId);

        // First call
        var res1 = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1))
                .andExpect(status().isCreated())
                .andReturn();

        var json1 = payload(res1);
        long orderId1 = json1.path("id").asLong();
        String code1 = json1.path("orderCode").asText();
        assertThat(orderId1).isPositive();

        // Exactly 1 order in db, 1 idempotency record
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_idempotency WHERE request_key = ?", Integer.class, key)).isEqualTo(1);
        // Stock reduced by 2 (10 -> 8)
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(8);

        // Second call with same key and same body
        var res2 = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1))
                .andExpect(status().isCreated())
                .andReturn();

        var json2 = payload(res2);
        assertThat(json2.path("id").asLong()).isEqualTo(orderId1);
        assertThat(json2.path("orderCode").asText()).isEqualTo(code1);

        // Still exactly 1 order in db and stock unchanged
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(8);

        // Third call with same key but DIFFERENT body -> 409 CONFLICT
        String conflictingBody = """
                {
                    "recipientName": "Tran Van B",
                    "recipientPhone": "0987654321",
                    "shippingAddress": "456 Hoan Kiem, Ha Noi",
                    "items": [{"productId": %d, "quantity": 2}]
                }
                """.formatted(productId);

        var resConflict = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(conflictingBody))
                .andExpect(status().isConflict())
                .andReturn();

        assertError(resConflict, 409, "IDEMPOTENCY_CONFLICT", "/api/orders");
        // Still 1 order in db
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }
}
