package com.swp.ckms.worker;

import com.swp.ckms.entity.NotificationJob;
import com.swp.ckms.enums.NotificationStatus;
import com.swp.ckms.repository.NotificationJobRepository;
import com.swp.ckms.service.EmailTemplateService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailWorker {

    private final NotificationJobRepository repository;
    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Scheduled(fixedDelayString = "${app.notification.worker-delay:10000}")
    @Transactional
    public void processPendingNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationJob> jobs = repository.findJobsToProcess(now, PageRequest.of(0, 50));
        
        if (jobs.isEmpty()) return;
        
        log.info("Processing {} pending notification jobs", jobs.size());

        for (NotificationJob job : jobs) {
            try {
                sendEmail(job);
                job.setStatus(NotificationStatus.SENT);
                job.setSentAt(LocalDateTime.now());
                log.info("Successfully sent email notification for job ID: {}", job.getId());
            } catch (Exception e) {
                handleFailure(job, e);
            }
            repository.save(job);
        }
    }

    private void sendEmail(NotificationJob job) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        if (job.getRecipientEmail() == null) throw new IllegalArgumentException("Recipient email is null");
        
        helper.setFrom(java.util.Objects.requireNonNullElse(fromEmail, "noreply@ckms.com"));
        helper.setTo(job.getRecipientEmail());
        helper.setSubject(java.util.Objects.requireNonNullElse(templateService.resolveSubject(job), "Thông báo CKMS"));
        helper.setText(java.util.Objects.requireNonNullElse(templateService.renderHtml(job), ""), true);

        mailSender.send(message);
    }

    private void handleFailure(NotificationJob job, Exception e) {
        log.error("Failed to send email for job ID: {}", job.getId(), e);
        
        int retries = job.getRetryCount() + 1;
        job.setRetryCount(retries);
        job.setErrorMessage(e.getMessage());
        job.setUpdatedAt(LocalDateTime.now());

        if (retries >= 3) {
            job.setStatus(NotificationStatus.FAILED_PERMANENT);
            log.error("Email job ID: {} reached maximum retries and marked as FAILED_PERMANENT", job.getId());
        } else {
            job.setStatus(NotificationStatus.FAILED);
            // Simple backoff: 1 min, 5 min, 15 min
            long delayMinutes = switch (retries) {
                case 1 -> 1;
                case 2 -> 5;
                default -> 15;
            };
            job.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
            log.info("Email job ID: {} status set to FAILED, scheduled for retry at {}", job.getId(), job.getNextRetryAt());
        }
    }
}
