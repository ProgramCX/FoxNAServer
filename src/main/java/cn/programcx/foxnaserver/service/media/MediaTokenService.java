package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.core.SubTokenService;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MediaTokenService implements SubTokenService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom RANDOM = new SecureRandom();
    private final long EXPIRE_SECONDS = 60;
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    @Override
    public String generateToken(String path) {
       String generatedToken = generateRandomString(8);
       redisTemplate.opsForValue().set(generatedToken, path,EXPIRE_SECONDS, TimeUnit.SECONDS);
        return generatedToken;
    }

    @Override
    public boolean validateToken(String token, String path) {
      String storedPath = redisTemplate.opsForValue().get(token);
      if (storedPath !=null&& !storedPath.isEmpty() && storedPath.equals(path)) {
          prolongToken(token, path);
          return true;
      }
      return false;
    }

    @Override
    public void prolongToken(String token, String path) {
       redisTemplate.opsForValue().set(token, path,EXPIRE_SECONDS, TimeUnit.SECONDS);
    }
}
