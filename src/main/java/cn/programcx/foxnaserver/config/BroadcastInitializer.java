package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 广播初始化器
 * 系统启动时执行必要的初始化工作
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BroadcastInitializer implements ApplicationRunner {

    private final TranscodeJobService transcodeJobService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始执行系统初始化...");
        
        // 恢复进行中的转码任务
        try {
            transcodeJobService.recoverRunningJobs();
        } catch (Exception e) {
            log.error("恢复转码任务失败: {}", e.getMessage());
        }
        
        log.info("系统初始化完成");
    }
}
