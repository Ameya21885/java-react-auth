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

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isEmpty() && authToken != null && !authToken.isEmpty()) {
            Twilio.init(accountSid, authToken);
        }
    }

    @Override
    public void sendOtp(String target, String otp) {
        String contentVariables = "{\"1\":\"" + otp + "\"}";

        Message message = Message.creator(
                new PhoneNumber("whatsapp:" + target),
                new PhoneNumber("whatsapp:" + fromWhatsApp),
                "Your OTP is " + otp // Fallback body
        )
        .setContentSid("HX229f5a04fd0510ce1b071852155d3e75")
        .setContentVariables(contentVariables)
        .create();

        System.out.println("Sent WhatsApp OTP SID: " + message.getSid());
    }

    @Override
    public boolean supports(String target) {
        // Basic check if it's a phone number (start with +)
        return target != null && target.matches("^\\+?[0-9]{10,15}$");
    }
}
