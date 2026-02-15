package cn.programcx.foxnaserver.config.oauth;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
// 死信队列监听（记录失败）
@Component
public class DLQConsumer {
    @RabbitListener(queues = TranscodeRabbitMQConfig.QUEUE_DLQ)
    public void onDeadLetter(TranscodeTask task) {
        log.error("转码任务彻底失败，需人工介入: {}, 路径: {}",
                task.getJobId(), task.getVideoPath());
        // 可在此发送邮件/钉钉通知
    }
}
