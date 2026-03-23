package com.swp.ckms.service;

public interface EmailService {
    void sendVerificationEmail(String to, String fullName, String username, String verificationLink);
    void sendResetPasswordEmail(String toEmail, String fullName, String resetLink);
}
