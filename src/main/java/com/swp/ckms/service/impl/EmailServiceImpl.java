package com.swp.ckms.service.impl;

import com.swp.ckms.service.EmailService;
import com.swp.ckms.util.EmailUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailUtils emailUtils;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Override
    public void sendVerificationEmail(String to, String fullName, String username, String verificationLink) {
        try {
            log.info("Sending verification email to {}", to);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(emailUtils.getVerificationEmailSubject());
            helper.setText(emailUtils.getVerificationEmailBody(fullName, username, verificationLink), true);

            mailSender.send(message);
            log.info("Verification email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}", to, e);
        }
    }

    @Override
    @Async
    public void sendResetPasswordEmail(String toEmail, String fullName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Yêu cầu đặt lại mật khẩu - CKMS");
            helper.setText(emailUtils.getResetPasswordEmailBody(fullName, resetLink), true);
            
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send reset password email", e);
        }
    }
}
