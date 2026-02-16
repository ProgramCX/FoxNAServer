package cn.programcx.foxnaserver.config.consumer;

import cn.programcx.foxnaserver.dto.media.CleanupTask;
import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import cn.programcx.foxnaserver.service.media.VideoFingerprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Slf4j
// 清理任务监听
@Component
@RequiredArgsConstructor
public class CleanupConsumer {

    private final VideoFingerprintService fingerprintService;
    private final TranscodeJobService transcodeJobService;

    @RabbitListener(queues = "task.cleanup") // 延迟队列到期后路由到这里
    public void onCleanup(CleanupTask task) throws IOException {
        log.info("清理临时文件: {}", task.getOutputDir());
        
        // 清理指纹绑定
        if (task.getFingerprint() != null && !task.getFingerprint().isEmpty()) {
            fingerprintService.removeFingerprint(task.getFingerprint());
        }
        
        // 清理临时文件
        Path outputPath = Path.of(task.getOutputDir());
        if (Files.exists(outputPath)) {
            Files.walk(outputPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            log.warn("无法删除文件: {}", file.getAbsolutePath());
                        }
                    });
        }
        
        log.info("清理完成: {}", task.getJobId());
    }
}
