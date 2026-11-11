package crm.ms.service;

import crm.ms.config.NotificationProperties;
import crm.ms.service.NotificationPersistenceService.DispatchSnapshot;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDispatchService {

    private final JavaMailSender javaMailSender;
    private final NotificationPersistenceService persistenceService;
    private final NotificationProperties notificationProperties;

    @Async("notificationTaskExecutor")
    public void dispatchAsync(UUID notificationId) {
        claimAndSend(notificationId);
    }

    /**
     * Atomically claims a PENDING or due RETRY notification and dispatches it via SMTP.
     * Returns true if this worker claimed the notification (whether SMTP succeeded or failed),
     * or false if another worker already claimed/completed it.
     */
    public boolean claimAndSend(UUID notificationId) {
        String workerPrefix = currentWorkerPrefix();
        Optional<DispatchSnapshot> claimed = persistenceService.claimForSending(notificationId, workerPrefix);
        if (claimed.isEmpty()) {
            log.debug("Notification {} was not claimable by worker {} (already claimed or not due)",
                    notificationId, workerPrefix);
            return false;
        }
        executeClaimedSend(claimed.get());
        return true;
    }

    /**
     * Dispatches a notification whose SENDING lease has already been claimed by the caller.
     * Passes snapshot.lockToken() back on completion so late/stale workers cannot overwrite newer leases.
     */
    public void executeClaimedSend(DispatchSnapshot snapshot) {
        if (!notificationProperties.getMail().isRecipientAllowed(snapshot.recipient())) {
            persistenceService.markPermanentFailure(
                    snapshot.id(),
                    snapshot.lockToken(),
                    "Recipient blocked by notification.mail.allowed-recipients safety allowlist: " + snapshot.recipient()
            );
            return;
        }

        try {
            MimeMessage mimeMessage = buildMimeMessage(snapshot);
            javaMailSender.send(mimeMessage);
            String messageId = safeReadMessageId(mimeMessage);
            boolean transitioned = persistenceService.markSent(snapshot.id(), snapshot.lockToken(), messageId);
            if (transitioned) {
                log.info("SMTP server accepted notification {} for recipient '{}'", snapshot.id(), snapshot.recipient());
            }
        } catch (NonRetryableMailException ex) {
            persistenceService.markPermanentFailure(snapshot.id(), snapshot.lockToken(), ex.getMessage());
        } catch (MailAuthenticationException | MailParseException ex) {
            persistenceService.markPermanentFailure(snapshot.id(), snapshot.lockToken(), describeException(ex));
        } catch (MailSendException ex) {
            if (isAuthenticationError(ex)) {
                persistenceService.markPermanentFailure(snapshot.id(), snapshot.lockToken(), describeException(ex));
            } else {
                persistenceService.markRetryOrDeadLetter(snapshot.id(), snapshot.lockToken(), describeException(ex));
            }
        } catch (MailException ex) {
            persistenceService.markRetryOrDeadLetter(snapshot.id(), snapshot.lockToken(), describeException(ex));
        } catch (Exception ex) {
            persistenceService.markRetryOrDeadLetter(snapshot.id(), snapshot.lockToken(), describeException(ex));
        }
    }

    private MimeMessage buildMimeMessage(DispatchSnapshot snapshot) {
        try {
            InternetAddress parsedRecipient = new InternetAddress(snapshot.recipient(), true);
            parsedRecipient.validate();

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            if (mimeMessage == null) {
                mimeMessage = new MimeMessage((Session) null);
            }

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            String fromAddress = notificationProperties.getMail().getFromAddress();
            String fromName = notificationProperties.getMail().getFromName();
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(parsedRecipient);
            helper.setSubject(snapshot.subject() != null ? snapshot.subject() : "");
            helper.setText(snapshot.body() != null ? snapshot.body() : "", snapshot.html());
            mimeMessage.saveChanges();
            return mimeMessage;
        } catch (AddressException ex) {
            throw new NonRetryableMailException("Invalid email address: " + ex.getMessage(), ex);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new NonRetryableMailException("Failed to build MIME email message: " + ex.getMessage(), ex);
        }
    }

    private boolean isAuthenticationError(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof AuthenticationFailedException || cursor instanceof MailAuthenticationException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String safeReadMessageId(MimeMessage message) {
        try {
            return message.getMessageID();
        } catch (MessagingException ex) {
            return null;
        }
    }

    private String describeException(Throwable ex) {
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        if (msg != null && !msg.isBlank()) {
            sb.append(": ").append(msg);
        }
        if (root != ex && root.getMessage() != null && !root.getMessage().isBlank()
                && (msg == null || !msg.contains(root.getMessage()))) {
            sb.append(" [cause: ").append(root.getClass().getSimpleName())
              .append(": ").append(root.getMessage()).append("]");
        }
        return sb.toString();
    }

    private String currentWorkerPrefix() {
        return "worker-" + ManagementFactory.getRuntimeMXBean().getName() + "-" + Thread.currentThread().getName();
    }

    private static final class NonRetryableMailException extends RuntimeException {
        private NonRetryableMailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
