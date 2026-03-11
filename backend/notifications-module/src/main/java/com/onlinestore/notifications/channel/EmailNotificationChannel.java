package com.onlinestore.notifications.channel;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    public void send(NotificationMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.body(), true);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            throw new RuntimeException("Email sending failed", ex);
        }
    }
}
