package cn.programcx.foxnaserver.config.consumer;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.mapper.TranscodeJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
// 死信队列监听（记录失败）
@Component
@RequiredArgsConstructor
public class DLQConsumer {

    private final TranscodeJobMapper transcodeJobMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = TranscodeRabbitMQConfig.QUEUE_DLQ)
    public void onDeadLetter(TranscodeTask task) {
        String jobId = task.getJobId();
        log.error("转码任务彻底失败，需人工介入: {}, 路径: {}",
                jobId, task.getVideoPath());

        // 确保MySQL和Redis状态一致为FAILED
        TranscodeJob dbJob = transcodeJobMapper.selectByJobId(jobId);
        if (dbJob != null && !TranscodeJob.Status.FAILED.name().equals(dbJob.getStatus())
                && !TranscodeJob.Status.CANCELLED.name().equals(dbJob.getStatus())) {
            transcodeJobMapper.updateFailed(jobId, "任务超过最大重试次数，进入死信队列");
        }

        // 同步Redis为FAILED
        JobStatus redisStatus = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
        if (redisStatus == null) {
            redisStatus = new JobStatus();
        }
        redisStatus.setState(JobStatus.State.FAILED);
        redisStatus.setMessage("任务超过最大重试次数，进入死信队列");
        redisTemplate.opsForValue().set("job:" + jobId, redisStatus, 8, TimeUnit.HOURS);

        // 可在此发送邮件/钉钉通知
    }
}
