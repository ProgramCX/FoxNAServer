package cn.programcx.foxnaserver.service.scheduler;

import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.jobs.DDNSJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class DDNSJobSchedulerService {

    @Autowired
    private Scheduler scheduler;

    public void scheduleJob(AccessTask accessTask) throws SchedulerException {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("accessTask", accessTask);

        JobDetail jobDetail = JobBuilder.newJob(DDNSJob.class)
                .withIdentity("ddnsJob_" + accessTask.getId())
                .usingJobData(jobDataMap)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("ddnsTrigger_" + accessTask.getId())
                .forJob(jobDetail)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(accessTask.getSyncInterval())
                        .repeatForever())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);

        if (!scheduler.isStarted()) {
            scheduler.start();
        }

        log.info("调度任务成功: {}", jobDetail.getKey().getName());
    }

    public void pauseJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + accessTask.getId());
        scheduler.pauseJob(jobKey);

        log.info("暂停任务: {}", jobKey.getName());
    }

    public void resumeJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + accessTask.getId());
        scheduler.resumeJob(jobKey);

        log.info("恢复任务: {}", jobKey.getName());
    }

    public void deleteJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + accessTask.getId());
        scheduler.deleteJob(jobKey);

        log.info("删除任务: {}", jobKey.getName());
    }

    public void rescheduleJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(accessTask.getTaskName());
        if (scheduler.checkExists(jobKey)) {
            deleteJob(accessTask);
        }
        scheduleJob(accessTask);

        log.info("重新调度任务: {}", jobKey.getName());
    }

}
