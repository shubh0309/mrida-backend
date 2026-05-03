package com.example.demo.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class razorpayPaymentService {

    @Value("${razorpay.key.id:}")
    private String keyId;

    @Value("${razorpay.key.secret:}")
    private String keySecret;

    private volatile RazorpayClient razorpayClient;

    private RazorpayClient clientOrThrow() {
        String id = trimToNull(keyId);
        String secret = trimToNull(keySecret);
        if (id == null || secret == null) {
            throw new IllegalStateException("Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET (or razorpay.key.* in application.properties)");
        }
        RazorpayClient local = razorpayClient;
        if (local == null) {
            synchronized (this) {
                local = razorpayClient;
                if (local == null) {
                    try {
                        local = new RazorpayClient(id, secret);
                    } catch (RazorpayException e) {
                        throw new IllegalStateException("Failed to initialise Razorpay client", e);
                    }
                    razorpayClient = local;
                }
            }
        }
        return local;
    }

    public JSONObject createStandardInrOrder(long amountPaise, String receipt, JSONObject notes) throws RazorpayException {
        if (amountPaise < 100) {
            throw new IllegalArgumentException("Razorpay requires INR amount of at least 100 paise (₹1.00)");
        }
        JSONObject payload = new JSONObject();
        payload.put("amount", amountPaise);
        payload.put("currency", "INR");
        payload.put("receipt", receipt);
        if (notes != null && notes.length() > 0) {
            payload.put("notes", notes);
        }
        Order order = clientOrThrow().orders.create(payload);
        return order.toJson();
    }

    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature)
            throws RazorpayException {
        String secret = trimToNull(keySecret);
        if (secret == null) {
            throw new IllegalStateException("Razorpay key secret missing");
        }
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", razorpayOrderId);
        options.put("razorpay_payment_id", razorpayPaymentId);
        options.put("razorpay_signature", razorpaySignature);
        return Utils.verifyPaymentSignature(options, secret);
    }

    /** Razorpay webhooks (`payment.captured` etc.) signing secret from dashboard differs from API secret unless configured similarly. */
    public boolean verifyWebhookSignature(String webhookBodyRaw, String signatureHeaderValue, String webhookSecret)
            throws RazorpayException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        if (signatureHeaderValue == null || signatureHeaderValue.isBlank()) {
            return false;
        }
        return Utils.verifyWebhookSignature(webhookBodyRaw, signatureHeaderValue, webhookSecret);
    }

    public String getPublishableKeyId() {
        String id = trimToNull(keyId);
        if (id == null) {
            throw new IllegalStateException("Razorpay key id missing");
        }
        return id;
    }

    private static String trimToNull(String val) {
        if (val == null) return null;
        String t = val.trim();
        return t.isEmpty() ? null : t;
    }
}
