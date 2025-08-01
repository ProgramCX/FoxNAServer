package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.mapper.AccessTaskMapper;
import cn.programcx.foxnaserver.service.scheduler.DDNSJobSchedulerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DDNSJobInitializer {
    private final AccessTaskMapper accessTaskMapper;
    private final DDNSJobSchedulerService schedulerService;

    @PostConstruct
    public void initJobsOnStartup() {
        List<AccessTask> tasks = accessTaskMapper.selectList(null);
        for (AccessTask task : tasks) {
            try {
                schedulerService.scheduleJob(task);
                log.info("启动初始化任务: {}", task.getId());
            } catch (Exception e) {
                log.error("启动初始化任务失败: {}", task.getId(), e);
            }
        }
    }
}
