package com.example.java_react_auth.module.auth.service.impl;

import com.example.java_react_auth.module.auth.service.NotificationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService implements NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public void sendOtp(String target, String otp) {
        if (mailPassword == null || mailPassword.isBlank()) {
            throw new IllegalStateException(
                    "Email is not configured: set MAIL_PASSWORD (Gmail app password) or spring.mail.password on the server.");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            Context context = new Context();
            context.setVariable("otp", otp);
            String htmlContent = templateEngine.process("otp-email", context);

            helper.setTo(target);
            helper.setSubject("Your OTP for Verification");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            System.out.println("Sent Email OTP to: " + target);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Override
    public boolean supports(String target) {
        if (mailPassword == null || mailPassword.isBlank()) {
            return false;
        }
        return target != null && target.contains("@");
    }
}
