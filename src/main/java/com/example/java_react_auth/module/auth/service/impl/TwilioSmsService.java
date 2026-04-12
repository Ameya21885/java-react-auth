package com.example.java_react_auth.module.auth.service.impl;

import com.example.java_react_auth.module.auth.service.NotificationService;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
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

    /** If set, OTP is sent as programmable SMS (avoids WhatsApp template/sandbox free-form limits). */
    @Value("${twilio.sms.from:}")
    private String smsFrom;

    /**
     * Optional Twilio Content API template SID for WhatsApp. Required for most business-initiated WhatsApp
     * messages (incl. sandbox first message); use Content Template Builder and variables like {@code {{1}}} for OTP.
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
        try {
            if (smsFrom != null && !smsFrom.isBlank()) {
                Message message = Message.creator(
                                new PhoneNumber(target),
                                new PhoneNumber(smsFrom.trim()),
                                body)
                        .create();
                System.out.println("Sent SMS OTP SID: " + message.getSid() + " status=" + message.getStatus());
                return;
            }

            boolean useTemplate = contentSid != null && !contentSid.isBlank();
            // Template-based WhatsApp: body must be empty; free-form only works inside the 24h session / after user messages you.
            var creator = Message.creator(
                    new PhoneNumber(toWhatsAppAddress(target)),
                    new PhoneNumber(toWhatsAppAddress(fromWhatsApp)),
                    useTemplate ? "" : body);
            if (useTemplate) {
                String escapedOtp = otp.replace("\\", "\\\\").replace("\"", "\\\"");
                creator = creator.setContentSid(contentSid.trim())
                        .setContentVariables("{\"1\":\"" + escapedOtp + "\"}");
            }

            Message message = creator.create();
            System.out.println("Sent WhatsApp OTP SID: " + message.getSid() + " status=" + message.getStatus());
        } catch (ApiException ex) {
            throw new IllegalStateException(twilioUserMessage(ex), ex);
        }
    }

    private static String twilioUserMessage(ApiException ex) {
        Integer c = ex.getCode();
        int code = c != null ? c : 0;
        String msg = "Twilio error " + code + ": " + ex.getMessage();
        if (code == 63016) {
            return msg + " — WhatsApp rejected a free-form OTP. Set TWILIO_WHATSAPP_CONTENT_SID to an approved "
                    + "WhatsApp Content template (variable 1 = OTP), or set TWILIO_SMS_FROM to a Twilio SMS number "
                    + "to send OTP as text instead.";
        }
        if (code == 21211 || code == 21608) {
            return msg + " — On a Twilio trial account, add and verify this destination number under "
                    + "Console → Phone Numbers → Verified Caller IDs.";
        }
        return msg + " — For WhatsApp sandbox, the user must first send Twilio's join phrase to +1 415 523 8886.";
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
