package cn.programcx.foxnaserver.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
@Slf4j
public class TranscodeRabbitMQConfig {
    // 交换机
    public static final String EXCHANGE_TRANSCODE = "foxnas.transcode";
    public static final String EXCHANGE_DLX = "foxnas.transcode.dlx";
    public static final String EXCHANGE_DELAY = "foxnas.cleanup.delay";

    // 队列
    public static final String QUEUE_NORMAL = "transcode.normal";
    public static final String QUEUE_PRIORITY = "transcode.priority"; // 立即观看
    public static final String QUEUE_DLQ = "transcode.dlq";           // 死信
    public static final String QUEUE_DELAY = "cleanup.delay";         // 延迟清理
    public static final String QUEUE_CLEANUP = "task.cleanup";        // 清理队列

    @Bean
    public DirectExchange transcodeExchange() {
        return new DirectExchange(EXCHANGE_TRANSCODE,true,false);
    }
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(EXCHANGE_DLX,true,false);
    }

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(EXCHANGE_DELAY, true, false);
    }

    // 后台批量转码
    @Bean
    public Queue normalQueue() {
        return QueueBuilder.durable(QUEUE_NORMAL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", "task.failed")
                .withArgument("x-message-ttl", 60 * 60 * 1000) // 60分钟
                .build();
    }

    // 立即观看转码
    @Bean
    public Queue priorityQueue() {
        return QueueBuilder.durable(QUEUE_PRIORITY)
                .withArgument("x-max-priority", 10)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", "task.failed")
                .build();
    }

    // 死信队列
    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    // 延迟队列：TTL到期后转回主交换机执行清理
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(QUEUE_DELAY)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TRANSCODE)
                .withArgument("x-dead-letter-routing-key", "task.cleanup")
                .withArgument("x-message-ttl", 7 * 24 * 60 * 60 * 1000) // 7天
                .build();
    }

    // 清理队列
    @Bean
    public Queue cleanupQueue() {
        return QueueBuilder.durable(QUEUE_CLEANUP).build();
    }

    @Bean
    public Binding bindingNormal() {
        return BindingBuilder.bind(normalQueue()).to(transcodeExchange()).with("task.normal");
    }

    @Bean
    public Binding bindingPriority() {
        return BindingBuilder.bind(priorityQueue()).to(transcodeExchange()).with("task.priority");
    }

    @Bean
    public Binding bindingDLQ() {
        return BindingBuilder.bind(dlq()).to(dlxExchange()).with("task.failed");
    }

    @Bean
    public Binding bindingDelay() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with("task.delay");
    }

    @Bean
    public Binding bindingCleanup() {
        return BindingBuilder.bind(cleanupQueue()).to(transcodeExchange()).with("task.cleanup");
    }

    // 处理JSON序列化
    @Bean
    public MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 配置RabbitTemplate，使用JSON转换器并添加发送确认回调
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(jsonConverter());
        template.setConfirmCallback((cd, ack, cause) -> {
            if (!ack) log.error("消息发送失败: {}", cause);
        });
        return template;
    }

}
