package cn.programcx.foxnaserver.jobs;

import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.entity.ErrorLog;
import cn.programcx.foxnaserver.mapper.AccessSecretMapper;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.util.DDNSUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DDNSJob extends QuartzJobBean {

    @Autowired DDNSUtil ddnsUtil;

    @Autowired
    AccessSecretMapper accessSecretMapper;
    @Autowired
    private ErrorLogService errorLogService;

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
                try {
                    ddnsUtil.updateDNSIpRecordAlibaba(accessTask);
                    accessTask.setLastFailed(false);
                    log.info("阿里云DDNS更新成功: id={}, domain={}, rr={}", accessTask.getId(), accessTask.getMainDomain(), accessTask.getDomainRr());
                    LambdaUpdateWrapper<AccessTask> updateWrapper = new LambdaUpdateWrapper<>();
                    try {
                        updateWrapper.eq(AccessTask::getId, accessTask.getId())
                                .set(AccessTask::isLastFailed, false);
                    }catch (Exception e){
                        log.error("更新任务状态失败: {}", e.getMessage());
                    }

                }
                catch (Exception e) {
                    if(!accessTask.isLastFailed()){
                        accessTask.setLastFailed(true);
                        log.error("阿里云DDNS更新失败: {}", e.getMessage());
                        errorLogService.insertErrorLog(String.format("阿里云DDNS更新失败：id:%d;domain:%s;rr:%s",accessTask.getId(),accessTask.getMainDomain(),accessTask.getDomainRr()), e
                        ,"DDNSJob","none","none","none");
                       try{
                           LambdaUpdateWrapper<AccessTask> updateWrapper = new LambdaUpdateWrapper<>();
                           updateWrapper.eq(AccessTask::getId, accessTask.getId())
                                   .set(AccessTask::isLastFailed, true);
                       }catch (Exception e2){
                            log.error("更新任务状态失败: {}", e2.getMessage());
                       }
                    }
                }
                break;
            default:
                throw new JobExecutionException("Unsupported DNS provider code: " + accessSecret.getDnsCode());
        }
    }
}
