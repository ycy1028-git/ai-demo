package com.aip.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${mail.default-encoding:UTF-8}")
    private String defaultEncoding;

    /**
     * 发送简单文本邮件
     */
    public boolean sendSimpleMail(String to, String subject, String content) {
        return sendSimpleMail(List.of(to), subject, content);
    }

    /**
     * 发送简单文本邮件（多人）
     */
    public boolean sendSimpleMail(List<String> toList, String subject, String content) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件发送失败：发件人地址未配置");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toList.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", toList, subject);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送 HTML 邮件
     */
    public boolean sendHtmlMail(String to, String subject, String htmlContent) {
        return sendHtmlMail(List.of(to), subject, htmlContent);
    }

    /**
     * 发送 HTML 邮件（多人）
     */
    public boolean sendHtmlMail(List<String> toList, String subject, String htmlContent) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件发送失败：发件人地址未配置");
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, defaultEncoding);
            helper.setFrom(fromAddress);
            helper.setTo(toList.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("HTML邮件发送成功: to={}, subject={}", toList, subject);
            return true;
        } catch (MessagingException e) {
            log.error("HTML邮件构建失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("HTML邮件发送失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送带附件的邮件
     */
    public boolean sendAttachmentMail(String to, String subject, String content, String[] attachmentPaths) {
        return sendAttachmentMail(List.of(to), subject, content, attachmentPaths);
    }

    /**
     * 发送带附件的邮件（多人）
     */
    public boolean sendAttachmentMail(List<String> toList, String subject, String content, String[] attachmentPaths) {
        return sendAttachmentMail(toList, null, subject, content, attachmentPaths);
    }

    /**
     * 发送带抄送和附件的邮件
     */
    public boolean sendAttachmentMail(List<String> toList, List<String> ccList, String subject, String content, String[] attachmentPaths) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件发送失败：发件人地址未配置");
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, defaultEncoding);
            helper.setFrom(fromAddress);
            helper.setTo(toList.toArray(new String[0]));
            
            // 设置抄送地址
            if (ccList != null && !ccList.isEmpty()) {
                helper.setCc(ccList.toArray(new String[0]));
            }
            
            helper.setSubject(subject);
            helper.setText(content);

            // 添加附件
            if (attachmentPaths != null) {
                for (String path : attachmentPaths) {
                    if (path != null && !path.isBlank()) {
                        helper.addAttachment(path.substring(path.lastIndexOf("/") + 1),
                                new java.io.File(path));
                    }
                }
            }

            mailSender.send(mimeMessage);
            log.info("附件邮件发送成功: to={}, cc={}, subject={}, attachments={}", 
                    toList, ccList, subject, attachmentPaths);
            return true;
        } catch (MessagingException e) {
            log.error("附件邮件构建失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("附件邮件发送失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送带抄送的邮件
     */
    public boolean sendCcMail(List<String> toList, List<String> ccList, String subject, String content) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件发送失败：发件人地址未配置");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toList.toArray(new String[0]));
            
            // 设置抄送地址
            if (ccList != null && !ccList.isEmpty()) {
                message.setCc(ccList.toArray(new String[0]));
            }
            
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("抄送邮件发送成功: to={}, cc={}, subject={}", toList, ccList, subject);
            return true;
        } catch (Exception e) {
            log.error("抄送邮件发送失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送带抄送的HTML邮件
     */
    public boolean sendCcHtmlMail(List<String> toList, List<String> ccList, String subject, String htmlContent) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件发送失败：发件人地址未配置");
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, defaultEncoding);
            helper.setFrom(fromAddress);
            helper.setTo(toList.toArray(new String[0]));
            
            // 设置抄送地址
            if (ccList != null && !ccList.isEmpty()) {
                helper.setCc(ccList.toArray(new String[0]));
            }
            
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("抄送HTML邮件发送成功: to={}, cc={}, subject={}", toList, ccList, subject);
            return true;
        } catch (MessagingException e) {
            log.error("抄送HTML邮件构建失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("抄送HTML邮件发送失败: to={}, error={}", toList, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试邮件配置是否正确（发送测试邮件）
     */
    public boolean testConnection() {
        try {
            if (fromAddress == null || fromAddress.isBlank()) {
                log.warn("邮件配置测试失败：发件人地址未配置");
                return false;
            }
            // 发送测试邮件验证配置
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(fromAddress);
            message.setSubject("AI智答平台邮件配置测试");
            message.setText("邮件配置成功！");
            mailSender.send(message);
            log.info("邮件配置测试成功");
            return true;
        } catch (Exception e) {
            log.error("邮件配置测试失败: {}", e.getMessage(), e);
            return false;
        }
    }
}