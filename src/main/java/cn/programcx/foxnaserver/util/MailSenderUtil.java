package cn.programcx.foxnaserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class MailSenderUtil {
    private static final Logger logger = LoggerFactory.getLogger(MailSenderUtil.class);
    
    @Value("${spring.mail.username}")
    private String mailUsername;

    @Autowired
    private JavaMailSender mailSender;

    public void sendMail(String to, String subject, String content) throws Exception {
        try {
            // 使用 MimeMessage 支持 HTML 和更复杂的邮件内容
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);  // false 表示发送纯文本，true 表示发送 HTML
            helper.setFrom(mailUsername);
            
            mailSender.send(message);
            logger.info("邮件发送成功: 收件人={}, 主题={}", to, subject);
        } catch (MessagingException e) {
            logger.error("邮件发送失败: 收件人={}, 主题={}, 错误={}", to, subject, e.getMessage(), e);
            throw new Exception("邮件发送失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("邮件发送异常: 收件人={}, 错误={}", to, e.getMessage(), e);
            throw e;
        }
    }
}
