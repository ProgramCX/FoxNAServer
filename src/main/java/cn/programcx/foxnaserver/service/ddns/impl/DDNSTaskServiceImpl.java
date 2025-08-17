package cn.programcx.foxnaserver.service.ddns.impl;

import cn.programcx.foxnaserver.dto.ddns.DDNSTaskStatus;
import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.mapper.AccessTaskMapper;
import cn.programcx.foxnaserver.service.ddns.DDNSTaskService;
import cn.programcx.foxnaserver.service.scheduler.DDNSJobSchedulerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DDNSTaskServiceImpl extends ServiceImpl<AccessTaskMapper, AccessTask> implements DDNSTaskService {
    @Autowired
    private DDNSJobSchedulerService ddnsJobSchedulerService;

    public void enableTask(Long id) throws Exception {
        AccessTask accessTask = this.getById(id);
        if (accessTask == null) {
            throw new Exception("AccessTask not found for ID: " + id);
        }
        accessTask.setStatus(1); // 1表示启用状态
        this.updateById(accessTask);
    }

    @Override
    public void disableTask(Long id) throws Exception {
        //禁用逻辑
        AccessTask accessTask = this.getById(id);
        if (accessTask == null) {
            throw new Exception("AccessTask not found for ID: " + id);
        }
        accessTask.setStatus(0); // 0表示禁用状态
        this.updateById(accessTask);
    }

    @Override
    public void restartTask(Long id) throws Exception {
        //重启逻辑
        stopTask(id);
        startTask(id);
    }

    @Override
    public void startTask(Long id) throws Exception {
        AccessTask accessTask = this.getById(id);
        if (accessTask == null) {
            throw new Exception("AccessTask not found for ID: " + id);
        }
        ddnsJobSchedulerService.startJob(accessTask);
    }

    @Override
    public void stopTask(Long id) throws Exception {
        ddnsJobSchedulerService.stopJob(id);
    }

    @Override
    public void resumeTask(Long id) throws Exception {

        ddnsJobSchedulerService.resumeJob(id);
    }

    @Override
    public void pauseTask(Long id) throws Exception {
        ddnsJobSchedulerService.pauseJob(id);
    }

    @Override
    public DDNSTaskStatus getTaskStatus(Long id) throws Exception{
        if(ddnsJobSchedulerService.existJob(id)){
            AccessTask accessTask = this.getById(id);
            if(accessTask.getStatus()==0) {
                return new DDNSTaskStatus(id,"disabled");
            }else{
                Trigger.TriggerState state = ddnsJobSchedulerService.getTriggerStatus(id);
                if (state == null) {
                    return new DDNSTaskStatus(id, "stopped");
                }
                return switch (state) {
                    case NORMAL -> new DDNSTaskStatus(id, "running");
                    case PAUSED -> new DDNSTaskStatus(id, "paused");
                    case COMPLETE -> new DDNSTaskStatus(id, "completed");
                    case BLOCKED -> new DDNSTaskStatus(id, "blocked");
                    case ERROR -> new DDNSTaskStatus(id, "error");
                    default -> new DDNSTaskStatus(id, "unknown");
                };
            }
        }else{
            return new DDNSTaskStatus(id, "stopped");
        }
    }

}
