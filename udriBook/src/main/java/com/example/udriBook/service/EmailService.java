package com.example.udriBook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendEmail(String toEmail, String subject, String body) {
        try {
            log.info("Sending email to: {}", toEmail);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setText(body);
            message.setSubject(subject);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            // Log error but do NOT throw exception so that OTP process continues
            log.error(
                    "Failed to send email to {} (Authentication Error likely). Continuing with Console OTP. Error: {}",
                    toEmail, e.getMessage());
        }
    }

    public void sendOtpEmail(String toEmail, String otp) {
        // Log OTP to console for testing/debugging purposes
        log.info("Generated OTP for {}: {}", toEmail, otp);

        String subject = "Your OTP for UdriBook Login";
        String body = "Hello,\n\n" +
                "Your One Time Password (OTP) for login is: " + otp + "\n\n" +
                "This OTP is valid for 5 minutes.\n" +
                "Do not share this OTP with anyone.\n\n" +
                "Best Regards,\n" +
                "UdriBook Team";

        sendEmail(toEmail, subject, body);
    }
}
