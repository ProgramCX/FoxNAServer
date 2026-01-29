package cn.programcx.foxnaserver.service.auth;

import org.springframework.stereotype.Service;

@Service
public interface VerificationService {
    void sendVerificationCode(String to) throws Exception;
    void verifyCode(String to, String code) throws Exception;
}
