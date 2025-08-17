package cn.programcx.foxnaserver.service.ddns;

import cn.programcx.foxnaserver.dto.ddns.DDNSTaskStatus;
import cn.programcx.foxnaserver.entity.AccessTask;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DDNSTaskService extends IService<AccessTask> {
    public void enableTask(Long id) throws Exception;
    public void disableTask(Long id) throws Exception;
    public void restartTask(Long id) throws Exception;
    public void startTask(Long id) throws Exception;
    public void stopTask(Long id) throws Exception;

    public void resumeTask(Long id) throws Exception;

    public void pauseTask(Long id) throws Exception;
    public DDNSTaskStatus getTaskStatus(Long id) throws Exception;
}
