package cn.programcx.foxnaserver.service.auth.impl;

import cn.programcx.foxnaserver.exception.VerificationCodeColdTimeException;
import cn.programcx.foxnaserver.service.auth.VerificationService;
import cn.programcx.foxnaserver.util.MailSenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

@Service
public class VerificationServiceImpl implements VerificationService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MailSenderUtil mailSenderUtil;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Override
    public void sendVerificationCode(String to) throws Exception {
        logger.info("开始发送验证码，邮箱: {}", to);
        try {
            String code = generateVerificationCode(to);
            sendCodeMail(to, code);
            logger.info("验证码发送成功，邮箱: {}", to);
        } catch (VerificationCodeColdTimeException e) {
            logger.warn("验证码冷却期中，邮箱: {}", to);
            throw e;
        } catch (Exception e) {
            logger.error("发送验证码失败，邮箱: {}, 错误: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void verifyCode(String to, String code) throws Exception {
        String key = "VERIFICATION_CODE_" + to;
        String codeKey = "VERIFICATION_CODE_COLD_" + to;
        String storedCode = redisTemplate.opsForValue().get(key);
        logger.info("开始验证验证码，邮箱: {}, 输入验证码: {}", to, code);
        logger.info("存储的验证码: {}, key: {}", storedCode, key);

        if (storedCode == null) {
            logger.warn("验证码已过期或不存在，邮箱: {}", to);
            throw new Exception("验证码已过期或不存在");
        }
        if (!storedCode.equals(code)) {
            logger.warn("验证码不匹配，邮箱: {}", to);
            throw new Exception("验证码不匹配");
        }
        // 验证成功后删除验证码
        redisTemplate.delete(key);
        redisTemplate.delete(codeKey);
        logger.info("验证码验证成功，邮箱: {}", to);
    }

    private String generateVerificationCode(String to) throws VerificationCodeColdTimeException {
        // 用户获取验证码唯一键值对
        String key = "VERIFICATION_CODE_" + to;
        // 冷却时间键值对
        String codeKey = "VERIFICATION_CODE_COLD_" + to;
        String code = redisTemplate.opsForValue().get(codeKey);

        if (code != null) {
            Long expire = redisTemplate.getExpire(codeKey);
            logger.warn("验证码冷却期中，邮箱: {}, 剩余时间: {} 秒", to, expire);
            throw new VerificationCodeColdTimeException(expire);
        }

        // 生成新的验证码
        String newCode = generateNewCode();

        // 设置冷却时间为60秒，验证码有效期10分钟
        redisTemplate.opsForValue().set(codeKey, newCode, 60L, java.util.concurrent.TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(key, newCode, 10 * 60L, java.util.concurrent.TimeUnit.SECONDS);

        logger.debug("生成新验证码，邮箱: {}, 验证码: {}", to, newCode);
        return newCode;
    }

    // 生成验证码
    private String generateNewCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    private void sendCodeMail(String to, String code) throws Exception {
        Context context = new Context();
        context.setVariable("code",code);
        String htmlContent = templateEngine.process("emailCodeTemplate", context); // emailTemplate 是模板的文件名
        String subject = "FoxNAS 验证码";
        mailSenderUtil.sendMail(to, subject, htmlContent);
    }
}
