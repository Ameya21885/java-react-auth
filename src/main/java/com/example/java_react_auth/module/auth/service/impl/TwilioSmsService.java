package com.example.java_react_auth.module.auth.service.impl;

import com.example.java_react_auth.module.auth.service.NotificationService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioSmsService implements NotificationService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.from}")
    private String fromWhatsApp;

    /**
     * Optional Twilio Content API template SID. Leave empty to send a plain WhatsApp body (works inside
     * the 24-hour session or for sandbox testing; first contact may still require sandbox opt-in).
     */
    @Value("${twilio.whatsapp.content-sid:}")
    private String contentSid;

    @PostConstruct
    public void init() {
        if (isTwilioConfigured()) {
            Twilio.init(accountSid.trim(), authToken.trim());
        }
    }

    private boolean isTwilioConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank();
    }

    private static String toWhatsAppAddress(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            return t;
        }
        return "whatsapp:" + t;
    }

    @Override
    public void sendOtp(String target, String otp) {
        if (!isTwilioConfigured()) {
            throw new IllegalStateException(
                    "Twilio is not configured: set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN on the server.");
        }

        String body = "Your OTP is " + otp;
        var creator = Message.creator(
                new PhoneNumber(toWhatsAppAddress(target)),
                new PhoneNumber(toWhatsAppAddress(fromWhatsApp)),
                body);

        if (contentSid != null && !contentSid.isBlank()) {
            creator = creator.setContentSid(contentSid.trim()).setContentVariables("{\"1\":\"" + otp + "\"}");
        }

        Message message = creator.create();
        System.out.println("Sent WhatsApp OTP SID: " + message.getSid());
    }

    @Override
    public boolean supports(String target) {
        if (!isTwilioConfigured()) {
            return false;
        }
        // Normalized phones from AuthService are E.164 with leading +
        return target != null
                && target.startsWith("+")
                && !target.contains("@")
                && target.substring(1).matches("\\d{8,14}");
    }
}
