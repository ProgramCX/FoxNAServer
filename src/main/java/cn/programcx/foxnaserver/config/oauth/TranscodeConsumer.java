package cn.programcx.foxnaserver.config.oauth;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import cn.programcx.foxnaserver.service.media.HLSTranscodeService;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranscodeConsumer {

    private final HLSTranscodeService transcodeService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

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

        try {
            updateStatus(jobId, JobStatus.State.PROCESSING, 0, null);

            // 执行转码
            transcodeService.transcode(task, 60*60);

            updateStatus(jobId, JobStatus.State.COMPLETED, 100,
                    "/api/media/stream/" + jobId + "/playlist.m3u8");

            // 发送延迟清理消息（7天后）
            rabbitTemplate.convertAndSend(
                    TranscodeRabbitMQConfig.EXCHANGE_DELAY,
                    "task.delay",
                    new CleanupTask(jobId, task.getOutputDir())
            );

            channel.basicAck(tag, false); // 确认消费

        } catch (Exception e) {
            log.error("转码失败: {}, 错误: {}", jobId, e.getMessage());

            if (task.getRetryCount() < 3) {
                // 重试：重新入队
                task.setRetryCount(task.getRetryCount() + 1);
                String key = isPriority ? "task.priority" : "task.normal";
                rabbitTemplate.convertAndSend(
                        TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE, key, task);
                channel.basicAck(tag, false);
            } else {
                // 进入死信队列
                updateStatus(jobId, JobStatus.State.FAILED, 0, e.getMessage());
                channel.basicNack(tag, false, false); // requeue=false
            }
        }
    }

    private void updateStatus(String jobId, JobStatus.State state, double progress, String hls) {
        JobStatus s = new JobStatus();
        s.setState(state);
        s.setProgress(progress);
        s.setHlsPath(hls);
        redisTemplate.opsForValue().set("job:" + jobId, s, 8, TimeUnit.HOURS);
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CleanupTask implements Serializable {
    private String jobId;
    private String outputDir;
}