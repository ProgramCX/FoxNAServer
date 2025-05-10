package cn.programcx.foxnaserver.config;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cn.programcx.foxnaserver.jobs.BroadcastJob;

@Configuration
public class BroadcastConfiguration {

    @Bean
    public JobDetail serviceSearchBroadcastJob() {
        return JobBuilder.newJob(BroadcastJob.class).withIdentity("serviceSearchBroadcastJob").storeDurably().build();
    }

    @Bean
    public Trigger serviceSearchBroadcastTrigger() {
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule("*/5 * * * * ?");
        return TriggerBuilder.newTrigger().forJob("serviceSearchBroadcastJob").withIdentity("serviceSearchBroadcastTrigger").withSchedule(cronScheduleBuilder).build();
    }
}
