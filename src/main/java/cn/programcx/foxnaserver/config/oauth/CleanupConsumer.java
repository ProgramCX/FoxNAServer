package cn.programcx.foxnaserver.config.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Slf4j
// 清理任务监听
@Component
public class CleanupConsumer {
    @RabbitListener(queues = "task.cleanup") // 延迟队列到期后路由到这里
    public void onCleanup(CleanupTask task) throws IOException {
        log.info("清理临时文件: {}", task.getOutputDir());
        Files.walk(Path.of(task.getOutputDir()))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
