package cn.programcx.foxnaserver.service.scheduler;

import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.jobs.DDNSJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.jdbcjobstore.TriggerStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public void pauseJob(Long id) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + id);
        scheduler.pauseJob(jobKey);

        log.info("暂停任务: {}", jobKey.getName());
    }

    public void startJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + accessTask.getId());
        if (!scheduler.checkExists(jobKey)) {
            scheduleJob(accessTask);
        } else {
            scheduler.resumeJob(jobKey);
        }

        log.info("启动任务: {}", jobKey.getName());
    }

    public void resumeJob(Long id) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + id);
        scheduler.resumeJob(jobKey);

        log.info("恢复任务: {}", jobKey.getName());
    }

    public void stopJob(Long id) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + id);
        scheduler.deleteJob(jobKey);

        log.info("删除任务: {}", jobKey.getName());
    }


    public void rescheduleJob(AccessTask accessTask) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(accessTask.getTaskName());
        if (scheduler.checkExists(jobKey)) {
            stopJob(accessTask.getId());
        }
        scheduleJob(accessTask);

        log.info("重新调度任务: {}", jobKey.getName());
    }

    public boolean existJob(Long id) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + id);
        boolean exists = scheduler.checkExists(jobKey);
        log.debug("检查任务是否存在: {}, 存在: {}", jobKey.getName(), exists);
        return exists;
    }

    public Trigger.TriggerState getTriggerStatus(Long id) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey("ddnsJob_" + id);
        TriggerKey triggerKey = TriggerKey.triggerKey("ddnsTrigger_" + id);

        if (!scheduler.checkExists(jobKey) || !scheduler.checkExists(triggerKey)) {
            log.warn("任务或触发器不存在: {}", jobKey.getName());
            return null;
        }

        Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
        log.info("获取任务状态: {}, 状态: {}", jobKey.getName(), triggerState);
        return triggerState;
    }

}
