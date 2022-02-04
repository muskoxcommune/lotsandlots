package io.lotsandlots.util;

import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EmailHelper {

    private static final Logger LOG = LoggerFactory.getLogger(EmailHelper.class);
    private static final Config CONFIG = ConfigWrapper.getConfig();

    public static final String SUBJECT_PREFIX = "[lotsandlots] ";

    private final List<String> notificationAddresses = new ArrayList<>();
    private final Properties properties = new Properties();

    private boolean isNotificationEnabled = false;
    private String notificationSender = "lotsandlots@localhost";
    private String sesConfigurationSet = "";
    private String smtpPassword = "";
    private String smtpUser = "";

    public EmailHelper() {
        properties.put("mail.transport.protocol", "smtp");
        if (CONFIG.hasPath("mail.enableNotifications")) {
            isNotificationEnabled = CONFIG.getBoolean("mail.enableNotifications");
        }
        if (CONFIG.hasPath("mail.notificationAddresses")) {
            notificationAddresses.addAll(CONFIG.getStringList("mail.notificationAddresses"));
        }
        if (CONFIG.hasPath("mail.notificationSender")) {
            notificationSender = CONFIG.getString("mail.notificationSender");
        }
        if (CONFIG.hasPath("mail.sesConfigurationSet")) {
            sesConfigurationSet = CONFIG.getString("mail.sesConfigurationSet");
        }
        if (CONFIG.hasPath("mail.smtpHost")) {
            properties.put("mail.smtp.host", CONFIG.getString("mail.smtpHost"));
            if (CONFIG.hasPath("mail.useTls") && CONFIG.getBoolean("mail.useTls")) {
                properties.put("mail.smtp.starttls.enable", true);
                properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
            }
        } else {
            properties.put("mail.smtp.host", "localhost");
        }
        if (CONFIG.hasPath("mail.smtpPassword")) {
            smtpPassword = CONFIG.getString("mail.smtpPassword");
        }
        if (CONFIG.hasPath("mail.smtpPort")) {
            properties.put("mail.smtp.port", CONFIG.getInt("mail.smtpPort"));
        }
        if (CONFIG.hasPath("mail.smtpUser")) {
            smtpUser = CONFIG.getString("mail.smtpUser");
        }
        if (StringUtils.isNotBlank(smtpPassword) && StringUtils.isNotBlank(smtpUser)) {
            properties.put("mail.smtp.auth", true);
        }
        LOG.debug("SMTP properties {}", properties);
    }

    public void sendMessage(String subjectString, String messageString) {
        if (!isNotificationEnabled) {
            return;
        }
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });
        for (String address : notificationAddresses) {
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(notificationSender));
                if (StringUtils.isNotBlank(sesConfigurationSet)) {
                    message.setHeader("X-SES-CONFIGURATION-SET", "Default");
                }
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(address));
                message.setSubject(SUBJECT_PREFIX + subjectString);
                message.setText(messageString);
                Transport.send(message);
            } catch (MessagingException e) {
                LOG.error("Failed to send notification email to {}", address, e);
            }
        }
    }
}
