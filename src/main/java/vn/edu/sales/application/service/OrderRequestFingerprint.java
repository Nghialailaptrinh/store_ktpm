package vn.edu.sales.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Collectors;

public class OrderRequestFingerprint {

    public String forDirectOrder(OrderCommandService.DirectOrderRequest request) {
        if (request == null) {
            return sha256("");
        }
        String linesPart = "";
        if (request.lines() != null) {
            linesPart = request.lines().stream()
                    .filter(line -> line != null)
                    .sorted(Comparator.comparing(OrderService.CreateLine::productId, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(line -> line.productId() + ":" + line.quantity())
                    .collect(Collectors.joining(","));
        }
        String payload = "DIRECT|" +
                (request.customerEmail() != null ? request.customerEmail().trim().toLowerCase() : "") + "|" +
                (request.recipientName() != null ? request.recipientName().trim() : "") + "|" +
                (request.recipientPhone() != null ? request.recipientPhone().trim() : "") + "|" +
                (request.shippingAddress() != null ? request.shippingAddress().trim() : "") + "|" +
                linesPart;
        return sha256(payload);
    }

    public String forCartCheckout(OrderCommandService.CartCheckoutRequest request) {
        if (request == null) {
            return sha256("");
        }
        String payload = "CART|" +
                (request.customerEmail() != null ? request.customerEmail().trim().toLowerCase() : "") + "|" +
                (request.recipientName() != null ? request.recipientName().trim() : "") + "|" +
                (request.recipientPhone() != null ? request.recipientPhone().trim() : "") + "|" +
                (request.shippingAddress() != null ? request.shippingAddress().trim() : "");
        return sha256(payload);
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", e);
        }
    }
}
