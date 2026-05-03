package com.example.demo.controller;

import com.example.demo.dto.CheckoutOrderCreatedResponse;
import com.example.demo.dto.CreateCheckoutOrderRequest;
import com.example.demo.dto.IndiaStateCityDto;
import com.example.demo.dto.IndiaPinCodeLookupResponse;
import com.example.demo.dto.PaymentVerificationResponse;
import com.example.demo.dto.RazorpayPaymentVerifyRequest;
import com.example.demo.dto.TrackOrderItemResponse;
import com.example.demo.service.checkoutService;
import com.example.demo.service.indiaLocationReferenceService;
import com.example.demo.service.indiaPinCodeLookupService;
import com.example.demo.service.razorpayPaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
@RequestMapping("/api/checkout")
public class checkoutController {

    @Autowired
    private checkoutService checkout;

    @Autowired
    private indiaLocationReferenceService locations;

    @Autowired
    private indiaPinCodeLookupService pinCodeLookup;

    @Autowired
    private razorpayPaymentService razor;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @GetMapping("/reference/india/locations")
    public ResponseEntity<List<IndiaStateCityDto>> indiaLocations() {
        return ResponseEntity.ok(locations.allStates());
    }

    @GetMapping("/reference/india/pincode/{pinCode}")
    public ResponseEntity<IndiaPinCodeLookupResponse> lookupPinCode(@PathVariable("pinCode") String pinCode) {
        return ResponseEntity.ok(pinCodeLookup.resolve(pinCode));
    }

    @PostMapping("/orders")
    public ResponseEntity<CheckoutOrderCreatedResponse> createOrder(@Valid @RequestBody CreateCheckoutOrderRequest body)
            throws RazorpayException {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkout.createOrderAndPayment(body));
    }

    @PostMapping("/orders/payment/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(@Valid @RequestBody RazorpayPaymentVerifyRequest body)
            throws RazorpayException {
        return ResponseEntity.ok(checkout.verifyPaid(body));
    }

    @GetMapping("/orders/track")
    public ResponseEntity<List<TrackOrderItemResponse>> trackOrders(
            @RequestParam("email") String email,
            @RequestParam("lastName") String lastName
    ) {
        return ResponseEntity.ok(checkout.trackByEmailAndLastName(email, lastName));
    }

    /**
     * Configure the same URL in Razorpay Dashboard → Webhooks for <code>payment.captured</code>.
     * Raw body + signature header must match the secret you configure.
     */
    @PostMapping(value = "/payments/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> razorpayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) throws RazorpayException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!razor.verifyWebhookSignature(rawBody, signature, webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String evt = root.path("event").asText("");
        if (!"payment.captured".equals(evt)) {
            return ResponseEntity.ok().build();
        }

        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String rzPaymentId = paymentEntity.path("id").asText(null);
        String rzOrderId = paymentEntity.path("order_id").asText(null);

        checkout.applyWebhookCaptured(rzOrderId, rzPaymentId);
        return ResponseEntity.ok().build();
    }
}
