package cn.programcx.foxnaserver.config.oauth;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.SubtitleJobStatus;
import cn.programcx.foxnaserver.dto.media.SubtitleTranscodeTask;
import cn.programcx.foxnaserver.service.media.HLSTranscodeService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 字幕转码消费者
 * 处理字幕轨道转VTT格式的任务
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubtitleTranscodeConsumer {

    private final HLSTranscodeService transcodeService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") +
            java.io.File.separator + "foxnas" +
            java.io.File.separator + "transcode";

    /**
     * 监听字幕转码队列
     */
    @RabbitListener(queues = TranscodeRabbitMQConfig.QUEUE_SUBTITLE,
            concurrency = "3",
            ackMode = "MANUAL")
    public void onSubtitleTask(SubtitleTranscodeTask task, Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String jobId = task.getJobId();

        try {
            log.info("开始处理字幕转码任务 [{}]，视频 [{}]，字幕轨道 [{}]", 
                jobId, task.getVideoPath(), task.getSubtitleTrackIndex());

            updateStatus(jobId, SubtitleJobStatus.State.PROCESSING, 0, null, null);

            // 执行字幕提取和转换
            Path vttPath = transcodeService.extractSubtitleToVtt(task);

            // 验证文件是否成功生成
            if (!Files.exists(vttPath) || Files.size(vttPath) < 10) {
                throw new RuntimeException("字幕文件生成失败或为空");
            }

            // 构建访问URL
            String vttUrl = "/api/file/media/subtitle/" + jobId;
            
            updateStatus(jobId, SubtitleJobStatus.State.COMPLETED, 100, vttUrl, null);
            log.info("字幕转码任务 [{}] 完成，VTT路径: {}", jobId, vttUrl);

            // 发送延迟清理消息（1天后清理字幕文件）
            rabbitTemplate.convertAndSend(
                    TranscodeRabbitMQConfig.EXCHANGE_DELAY,
                    "task.delay",
                    new SubtitleCleanupTask(jobId, vttPath.toString())
            );

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("字幕转码失败: {}, 错误: {}", jobId, e.getMessage());

            if (task.getRetryCount() < 2) {
                // 重试
                task.setRetryCount(task.getRetryCount() + 1);
                rabbitTemplate.convertAndSend(
                        TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE,
                        TranscodeRabbitMQConfig.ROUTING_SUBTITLE,
                        task);
                channel.basicAck(tag, false);
            } else {
                // 进入死信队列
                updateStatus(jobId, SubtitleJobStatus.State.FAILED, 0, null, e.getMessage());
                channel.basicNack(tag, false, false);
            }
        }
    }

    private void updateStatus(String jobId, SubtitleJobStatus.State state, double progress, String vttPath, String message) {
        SubtitleJobStatus s = new SubtitleJobStatus();
        s.setState(state);
        s.setProgress(progress);
        s.setVttPath(vttPath);
        s.setMessage(message);
        
        if (state == SubtitleJobStatus.State.COMPLETED || state == SubtitleJobStatus.State.FAILED) {
            s.setCompleteTime(LocalDateTime.now());
        }
        
        redisTemplate.opsForValue().set("subtitle_job:" + jobId, s, 8, TimeUnit.HOURS);
    }

    /**
     * 字幕清理任务
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SubtitleCleanupTask implements java.io.Serializable {
        private String jobId;
        private String vttPath;
    }
}
