package com.example.demo.service;

import com.example.demo.dto.CheckoutOrderCreatedResponse;
import com.example.demo.dto.CreateCheckoutOrderRequest;
import com.example.demo.dto.DeliveryDetailsRequest;
import com.example.demo.dto.PaymentVerificationResponse;
import com.example.demo.dto.RazorpayPaymentVerifyRequest;
import com.example.demo.dto.TrackOrderItemResponse;
import com.example.demo.model.CheckoutUserInfo;
import com.example.demo.model.DeliveryAddress;
import com.example.demo.model.OrderFulfillmentStatus;
import com.example.demo.model.OrderLineItem;
import com.example.demo.model.Product;
import com.example.demo.model.ShopOrder;
import com.example.demo.repo.checkoutUserInfoRepo;
import com.example.demo.repo.deliveryAddressRepo;
import com.example.demo.repo.productRepo;
import com.example.demo.repo.shopOrderRepo;
import com.razorpay.RazorpayException;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.List;

@Service
public class checkoutService {

    @Autowired
    private productRepo products;

    @Autowired
    private deliveryAddressRepo addressRepo;

    @Autowired
    private checkoutUserInfoRepo userInfoRepo;

    @Autowired
    private shopOrderRepo orders;

    @Autowired
    private indiaLocationReferenceService locations;

    @Autowired
    private razorpayPaymentService razorpay;

    @Value("${checkout.receipt-prefix}")
    private String receiptPrefix;

    @Transactional
    public CheckoutOrderCreatedResponse createOrderAndPayment(CreateCheckoutOrderRequest request)
            throws RazorpayException {
        DeliveryDetailsRequest d = request.getDelivery();
        if (d == null) {
            throw new IllegalArgumentException("Delivery details required");
        }
        String normalizedPhone = normalizePhone(d.getPhoneNumber());
        if (!looksLikeIndianMobile(normalizedPhone)) {
            throw new IllegalArgumentException("Phone must resolve to a 10-digit Indian mobile number beginning with 6-9");
        }
        d.setPhoneNumber(normalizedPhone);

        String code = Optional.ofNullable(d.getStateCode())
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("State code is required"))
                .toUpperCase(Locale.ROOT);
        String stateDisplay = locations.resolveState(code)
                .map(cs -> cs.getName())
                .orElseGet(() -> Optional.ofNullable(d.getStateName()).orElse("").trim());
        if (stateDisplay.isBlank()) {
            throw new IllegalArgumentException("Unsupported state");
        }
        if (Optional.ofNullable(d.getCity()).map(String::trim).orElse("").isBlank()) {
            throw new IllegalArgumentException("City is required");
        }

        BigDecimal running = BigDecimal.ZERO;
        DeliveryAddress addr = persistAddress(d, code, stateDisplay);

        ShopOrder order = new ShopOrder();
        order.setFulfillmentStatus(OrderFulfillmentStatus.PAYMENT_PENDING);
        order.setDeliveryAddress(addr);

        int lineCount = request.getItems() == null ? 0 : request.getItems().size();
        if (lineCount == 0) throw new IllegalArgumentException("Cart is empty");

        for (var lit : request.getItems()) {
            Product p = products.findById(lit.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown productId: " + lit.getProductId()));
            int qty = lit.getQuantity();
            if (!p.isAvailable() || qty > p.getQuantity()) {
                throw new IllegalArgumentException("Insufficient quantity for product '" + p.getName() + "' (id="
                        + p.getId() + ")");
            }

            BigDecimal unit;
            try {
                unit = new BigDecimal(Optional.ofNullable(p.getPrice()).orElseThrow().trim()).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Product price malformed for productId " + p.getId());
            }
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            OrderLineItem li = new OrderLineItem(null, order, p.getId(), p.getName(), qty, unit, lineTotal);
            order.addLineItem(li);
            running = running.add(lineTotal).setScale(2, RoundingMode.HALF_UP);
        }

        long paiseTotal = rupeesToPaise(running);
        order.setTotalAmountInr(running);
        order.setTotalPaise(paiseTotal);

        ShopOrder saved = orders.save(order);

        JSONObject notes = new JSONObject();
        notes.put("merchant_order_id", saved.getId().toString());

        String receiptBase = sanitizeReceipt(receiptPrefix + saved.getId());
        JSONObject rzJson = razorpay.createStandardInrOrder(paiseTotal, receiptBase, notes);

        String rzOrderId = rzJson.optString("id", null);
        if (rzOrderId == null || rzOrderId.isBlank()) {
            throw new IllegalStateException("Razorpay order response missing id");
        }
        saved.setRazorpayOrderId(rzOrderId);
        orders.save(saved);

        String keyId = razorpay.getPublishableKeyId();
        return new CheckoutOrderCreatedResponse(saved.getId(), rzOrderId, paiseTotal, "INR", keyId);
    }

    /**
     * Call from browser after Razorpay.success handler with returned signature fields plus our DB order id for safety.
     */
    @Transactional
    public PaymentVerificationResponse verifyPaid(RazorpayPaymentVerifyRequest body) throws RazorpayException {
        ShopOrder order = orders.findById(body.getMerchantOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown merchant order"));

        if (order.getFulfillmentStatus() == OrderFulfillmentStatus.PAID) {
            return new PaymentVerificationResponse(order.getId(), order.getRazorpayOrderId(),
                    Optional.ofNullable(order.getRazorpayPaymentId()).orElse(""), order.getFulfillmentStatus());
        }

        if (!Optional.ofNullable(body.getRazorpayOrderId()).orElse("").equals(order.getRazorpayOrderId())) {
            throw new IllegalArgumentException("Razorpay order mismatch for this merchant order");
        }

        boolean ok = razorpay.verifyPaymentSignature(body.getRazorpayOrderId(), body.getRazorpayPaymentId(),
                body.getRazorpaySignature());
        if (!ok) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.PAYMENT_FAILED);
            orders.save(order);
            throw new IllegalArgumentException("Payment signature verification failed");
        }

        order.setFulfillmentStatus(OrderFulfillmentStatus.PAID);
        order.setRazorpayPaymentId(body.getRazorpayPaymentId());
        orders.save(order);

        adjustInventoryAfterPayment(order.getId());

        return new PaymentVerificationResponse(order.getId(), order.getRazorpayOrderId(),
                order.getRazorpayPaymentId(), order.getFulfillmentStatus());
    }

    @Transactional
    public Optional<ShopOrder> applyWebhookCaptured(String rzOrderId, String rzPaymentId) {
        if (rzOrderId == null || rzOrderId.isBlank()) return Optional.empty();
        ShopOrder order = orders.findByRazorpayOrderId(rzOrderId).orElse(null);
        if (order == null) return Optional.empty();

        if (order.getFulfillmentStatus() != OrderFulfillmentStatus.PAID) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.PAID);
            if (rzPaymentId != null && !rzPaymentId.isBlank()) {
                order.setRazorpayPaymentId(rzPaymentId);
            }
            orders.save(order);
            adjustInventoryAfterPayment(order.getId());
        }
        return Optional.of(order);
    }

    public List<TrackOrderItemResponse> trackByEmailAndLastName(String email, String lastName) {
        String normalizedEmail = Optional.ofNullable(email)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Email is required"));
        String normalizedLastName = Optional.ofNullable(lastName)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Last name is required"));

        return orders.findByTrackingIdentity(normalizedEmail, normalizedLastName).stream().map(order -> {
            LocalDate placed = order.getCreatedAt() == null ? LocalDate.now() : order.getCreatedAt().toLocalDate();
            LocalDate eta = addWorkingDays(placed, 5);
            String status = order.getFulfillmentStatus() == OrderFulfillmentStatus.PAID ? "IN_PROGRESS" : "PAYMENT_PENDING";
            return new TrackOrderItemResponse(
                    order.getId(),
                    status,
                    placed.toString(),
                    eta.toString()
            );
        }).toList();
    }

    private void adjustInventoryAfterPayment(long orderId) {
        ShopOrder aggregated = orders.findWithLineItemsById(orderId).orElse(null);
        if (aggregated == null || aggregated.isInventoryAdjusted()) {
            return;
        }

        aggregated.setInventoryAdjusted(true);
        orders.save(aggregated);

        aggregated.getLineItems().forEach(li -> products.findById(li.getProductId()).ifPresent(p -> {
            int next = Math.max(p.getQuantity() - li.getQuantity(), 0);
            p.setQuantity(next);
            p.setAvailable(next > 0);
            products.save(p);
        }));
    }

    private DeliveryAddress persistAddress(DeliveryDetailsRequest d, String code, String stateDisplay) {
        CheckoutUserInfo userInfo = new CheckoutUserInfo();
        userInfo.setRecipientName(d.getRecipientName().trim());
        userInfo.setLastName(d.getLastName().trim());
        userInfo.setEmail(d.getEmail().trim());
        userInfo.setPhoneNumber(normalizePhone(d.getPhoneNumber()));
        userInfo.setAddressLine1(d.getAddressLine1().trim());
        userInfo.setAddressLine2(Optional.ofNullable(d.getAddressLine2()).map(String::trim).filter(x -> !x.isEmpty()).orElse(null));
        userInfo.setPinCode(d.getPinCode().trim());
        userInfo.setLandmark(Optional.ofNullable(d.getLandmark()).map(String::trim).filter(x -> !x.isEmpty()).orElse(null));
        userInfo.setCity(d.getCity().trim());
        userInfo.setStateCode(code);
        userInfo.setStateName(stateDisplay);
        userInfoRepo.save(userInfo);

        DeliveryAddress addr = new DeliveryAddress();
        addr.setRecipientName(d.getRecipientName().trim());
        addr.setLastName(d.getLastName().trim());
        addr.setEmail(d.getEmail().trim());
        addr.setPhoneNumber(normalizePhone(d.getPhoneNumber()));
        String line1 = d.getAddressLine1().trim();
        String line2 = Optional.ofNullable(d.getAddressLine2()).map(String::trim).filter(x -> !x.isEmpty()).orElse(null);
        addr.setAddressLine(line2 == null ? line1 : (line1 + ", " + line2));
        addr.setAddressLine1(d.getAddressLine1().trim());
        addr.setAddressLine2(line2);
        addr.setPinCode(d.getPinCode().trim());
        addr.setLandmark(Optional.ofNullable(d.getLandmark()).map(String::trim).filter(x -> !x.isEmpty()).orElse(null));
        addr.setCity(d.getCity().trim());
        addr.setStateCode(code);
        addr.setStateName(stateDisplay);
        return addressRepo.save(addr);
    }

    private static boolean looksLikeIndianMobile(String digits10) {
        return digits10 != null && digits10.matches("^[6-9]\\d{9}$");
    }

    /**
     * Strips separators and recognises optional country prefixes before validation.
     */
    private static String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^\\d]", "");
        if (digits.length() >= 12 && digits.startsWith("91")) {
            return digits.substring(digits.length() - 10);
        }
        if (digits.length() == 11 && digits.charAt(0) == '0') {
            return digits.substring(1);
        }
        if (digits.length() == 10) {
            return digits;
        }
        return digits;
    }

    private static long rupeesToPaise(BigDecimal inrExactToDisplay) {
        return inrExactToDisplay.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static LocalDate addWorkingDays(LocalDate start, int days) {
        LocalDate date = start;
        int left = Math.max(days, 0);
        while (left > 0) {
            date = date.plusDays(1);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                left--;
            }
        }
        return date;
    }

    private static String sanitizeReceipt(String raw) {
        String noSpaces = raw.replaceAll("\\s+", "");
        return noSpaces.substring(0, Math.min(noSpaces.length(), 40));
    }
}
