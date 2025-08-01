package cn.programcx.foxnaserver.jobs;

import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.mapper.AccessSecretMapper;
import cn.programcx.foxnaserver.util.DDNSUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.SneakyThrows;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
public class DDNSJob extends QuartzJobBean {

    @Autowired DDNSUtil ddnsUtil;

    @Autowired
    AccessSecretMapper accessSecretMapper;

    @Override
    @SneakyThrows
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        AccessTask accessTask =(AccessTask) jobDataMap.get("accessTask");
        if (accessTask == null) {
            throw new JobExecutionException("AccessTask is null");
        }

        if(accessTask.getStatus() == 0) {
            //如果任务被暂停，则不执行
            return;
        }

        LambdaQueryWrapper<AccessSecret> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AccessSecret::getId, accessTask.getDnsSecretId());

        AccessSecret accessSecret = accessSecretMapper.selectOne(queryWrapper);

        if(accessSecret ==null){
            throw new JobExecutionException("AccessSecret not found for ID: " + accessTask.getDnsSecretId());
        }

        switch (accessSecret.getDnsCode()) {
            case 2: // 阿里云
                ddnsUtil.updateDNSIpRecordAlibaba(accessTask);
                break;
            default:
                throw new JobExecutionException("Unsupported DNS provider code: " + accessSecret.getDnsCode());
        }
    }
}
