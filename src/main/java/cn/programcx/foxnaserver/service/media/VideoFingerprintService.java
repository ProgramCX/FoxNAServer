package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.dto.media.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 视频指纹服务
 * 用于管理视频文件的指纹，避免重复转码
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFingerprintService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key 前缀
    private static final String FINGERPRINT_PREFIX = "fingerprint:";
    private static final String FINGERPRINT_TO_JOB_PREFIX = "fp2job:";
    
    // 指纹有效期：7天（与转码文件保留时间一致）
    private static final long FINGERPRINT_TTL_DAYS = 7;

    /**
     * 生成视频文件指纹
     * 基于文件路径、文件大小、最后修改时间生成唯一标识
     * 
     * @param videoPath 视频文件路径
     * @return 文件指纹 (MD5哈希)
     */
    public String generateFingerprint(String videoPath) {
        File file = new File(videoPath);
        if (!file.exists()) {
            throw new RuntimeException("文件不存在: " + videoPath);
        }

        // 组合关键信息：路径 + 大小 + 修改时间
        String fingerprintData = String.format("%s|%d|%d", 
            videoPath, 
            file.length(), 
            file.lastModified()
        );

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(fingerprintData.getBytes(StandardCharsets.UTF_8));
            return String.format("%032x", new BigInteger(1, hashBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }

    /**
     * 检查指纹是否已存在且转码已完成
     * 
     * @param fingerprint 文件指纹
     * @return 如果存在且已完成，返回jobId；否则返回null
     */
    public String getExistingJobId(String fingerprint) {
        String jobId = (String) redisTemplate.opsForValue().get(FINGERPRINT_TO_JOB_PREFIX + fingerprint);
        
        if (jobId == null) {
            return null;
        }

        // 检查对应的转码任务状态
        JobStatus status = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
        
        if (status == null) {
            // job已过期，清理指纹映射
            redisTemplate.delete(FINGERPRINT_TO_JOB_PREFIX + fingerprint);
            return null;
        }

        // 只有已完成的任务才返回
        if (status.getState() == JobStatus.State.COMPLETED) {
            return jobId;
        }

        // 任务进行中或失败，不返回jobId
        return null;
    }

    /**
     * 绑定指纹和jobId
     * 
     * @param fingerprint 文件指纹
     * @param jobId 转码任务ID
     */
    public void bindFingerprint(String fingerprint, String jobId) {
        String key = FINGERPRINT_TO_JOB_PREFIX + fingerprint;
        redisTemplate.opsForValue().set(key, jobId, FINGERPRINT_TTL_DAYS, TimeUnit.DAYS);
        log.info("绑定指纹 [{}] 到任务 [{}]，TTL={}天", fingerprint, jobId, FINGERPRINT_TTL_DAYS);
    }

    /**
     * 检查指纹对应的任务是否正在进行中
     * 
     * @param fingerprint 文件指纹
     * @return 如果任务正在进行中，返回jobId；否则返回null
     */
    public String getProcessingJobId(String fingerprint) {
        String jobId = (String) redisTemplate.opsForValue().get(FINGERPRINT_TO_JOB_PREFIX + fingerprint);
        
        if (jobId == null) {
            return null;
        }

        JobStatus status = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
        
        if (status == null) {
            redisTemplate.delete(FINGERPRINT_TO_JOB_PREFIX + fingerprint);
            return null;
        }

        if (status.getState() == JobStatus.State.PENDING || 
            status.getState() == JobStatus.State.PROCESSING) {
            return jobId;
        }

        return null;
    }

    /**
     * 删除指纹绑定（用于清理）
     * 
     * @param fingerprint 文件指纹
     */
    public void removeFingerprint(String fingerprint) {
        redisTemplate.delete(FINGERPRINT_TO_JOB_PREFIX + fingerprint);
        log.info("删除指纹绑定 [{}]", fingerprint);
    }
}
