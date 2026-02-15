package cn.programcx.foxnaserver.config.oauth;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.CleanupTask;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.mapper.TranscodeJobMapper;
import cn.programcx.foxnaserver.service.media.HLSTranscodeService;
import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import cn.programcx.foxnaserver.service.media.VideoFingerprintService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranscodeConsumer {

    private final HLSTranscodeService transcodeService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final VideoFingerprintService fingerprintService;
    private final TranscodeJobService transcodeJobService;
    private final TranscodeJobMapper transcodeJobMapper;

    // 监听普通队列
    @RabbitListener(queues = TranscodeRabbitMQConfig.QUEUE_NORMAL,
            concurrency = "2",
            ackMode = "MANUAL")
    public void onNormal(TranscodeTask task, Message msg, Channel channel) throws IOException {
        process(task, msg, channel, false);
    }

    // 监听高优先级队列
    @RabbitListener(queues = TranscodeRabbitMQConfig.QUEUE_PRIORITY,
            concurrency = "2",
            ackMode = "MANUAL")
    public void onPriority(TranscodeTask task, Message msg, Channel channel) throws IOException {
        process(task, msg, channel, true);
    }

    private void process(TranscodeTask task, Message msg, Channel channel, boolean isPriority) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String jobId = task.getJobId();

        // 检查数据库中任务状态，如果已被取消则直接确认消息
        TranscodeJob dbJob = transcodeJobService.getByJobId(jobId);
        if (dbJob != null && TranscodeJob.Status.CANCELLED.name().equals(dbJob.getStatus())) {
            log.info("任务 [{}] 已被取消，跳过处理", jobId);
            channel.basicAck(tag, false);
            return;
        }

        try {
            // 更新数据库状态为进行中
            transcodeJobService.updateProcessing(jobId);
            updateRedisStatus(jobId, JobStatus.State.PROCESSING, 0, null);

            // 执行转码
            transcodeService.transcode(task, 60 * 60);

            String hlsPath = "/api/file/media/stream/" + jobId + "/playlist.m3u8";

            int stage = 0;
            // 从Redis获取当前进度
            JobStatus redisJob = getRedisJob(jobId);
            if (redisJob != null) {
                stage = redisJob.getCurrentStage();
            }

            // 更新数据库为完成状态
            transcodeJobService.updateCompleted(jobId, hlsPath);
            transcodeJobMapper.updateProgress(jobId, 100.0, stage);

            updateRedisStatus(jobId, JobStatus.State.COMPLETED, 100, hlsPath);

            // 绑定指纹
            if (task.getFingerprint() != null && !task.getFingerprint().isEmpty()) {
                fingerprintService.bindFingerprint(task.getFingerprint(), jobId);
            }

            // 发送延迟清理消息（7天后）
            rabbitTemplate.convertAndSend(
                    TranscodeRabbitMQConfig.EXCHANGE_DELAY,
                    "task.delay",
                    new CleanupTask(jobId, task.getOutputDir(), task.getFingerprint())
            );

            channel.basicAck(tag, false); // 确认消费

        } catch (Exception e) {
            // 先检查是否是用户手动取消
            TranscodeJob cancelCheck = transcodeJobService.getByJobId(jobId);
            if (cancelCheck != null && TranscodeJob.Status.CANCELLED.name().equals(cancelCheck.getStatus())) {
                log.info("任务 [{}] 用户手动取消", jobId);
                updateRedisStatus(jobId, JobStatus.State.CANCELLED, 0, "用户请求取消");
                channel.basicAck(tag, false);
                return;
            }

            log.error("转码失败: {}, 错误: {}", jobId, e.getMessage());

            if (task.getRetryCount() < 3) {
                // 重试：更新状态为PENDING并重新入队
                transcodeJobService.updateFailed(jobId, e.getMessage());
                transcodeJobMapper.updateStatus(jobId, TranscodeJob.Status.PENDING.name());
                updateRedisStatus(jobId, JobStatus.State.PENDING, 0, null);

                task.setRetryCount(task.getRetryCount() + 1);
                String key = isPriority ? "task.priority" : "task.normal";
                rabbitTemplate.convertAndSend(
                        TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE, key, task);
                channel.basicAck(tag, false);
            } else {
                // 彻底失败，更新为FAILED并进入死信队列
                transcodeJobService.updateFailed(jobId, e.getMessage());
                updateRedisStatus(jobId, JobStatus.State.FAILED, 0, e.getMessage());
                channel.basicNack(tag, false, false); // requeue=false
            }
        }
    }

    private void updateRedisStatus(String jobId, JobStatus.State state, double progress, String hls) {
        // 先读取已有的Redis记录，保留stages/currentStage/createTime等字段
        JobStatus s = getRedisJob(jobId);
        if (s == null) {
            s = new JobStatus();
        }
        s.setState(state);
        s.setProgress(progress);
        if (hls != null) {
            if (state == JobStatus.State.FAILED || state == JobStatus.State.CANCELLED) {
                s.setMessage(hls); // hls参数在失败/取消时作为错误消息
            } else {
                s.setHlsPath(hls);
            }
        }
        redisTemplate.opsForValue().set("job:" + jobId, s, 8, TimeUnit.HOURS);
    }

    public JobStatus getRedisJob(String jobId) {
        return (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
    }
}
