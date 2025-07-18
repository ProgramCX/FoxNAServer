package cn.programcx.foxnaserver.service.ddns.impl;

import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.mapper.AccessTaskMapper;
import cn.programcx.foxnaserver.service.ddns.DDNSTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class DDNSTaskServiceImpl extends ServiceImpl<AccessTaskMapper, AccessTask> implements DDNSTaskService {
    public void enableTask(Long id) throws Exception {
        //启用逻辑
    }

    @Override
    public void disableTask(Long id) throws Exception {
        //禁用逻辑
    }

    @Override
    public void restartTask(Long id) throws Exception {
        //重启逻辑
    }

    @Override
    public void startTask(Long id) throws Exception {

    }

    @Override
    public void stopTask(Long id) throws Exception {

    }


}
