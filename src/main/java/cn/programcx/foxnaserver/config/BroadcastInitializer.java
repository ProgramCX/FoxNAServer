package cn.programcx.foxnaserver.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import cn.programcx.foxnaserver.jobs.BroadcastJob;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastInitializer {
    @Autowired
    private Scheduler scheduler;
    @PostConstruct
    public void initJobsOnStartup() {
        JobDetail jobDetail = JobBuilder.newJob(BroadcastJob.class)
                .withIdentity("serviceSearchBroadcastJob")
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("serviceSearchBroadcastJobTrigger" )
                .forJob(jobDetail)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(5)
                        .repeatForever())
                .build();

        try {
            scheduler.scheduleJob(jobDetail, trigger);
        }
        catch (Exception e){
            log.error("启动广播发现初始化任务失败: {}", e.getMessage(), e);
        }

    }
}

