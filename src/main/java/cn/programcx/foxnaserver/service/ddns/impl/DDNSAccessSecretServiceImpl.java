package cn.programcx.foxnaserver.service.ddns.impl;

import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.mapper.AccessSecretMapper;
import cn.programcx.foxnaserver.service.ddns.DDNSAccessSecretService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class DDNSAccessSecretServiceImpl extends ServiceImpl<AccessSecretMapper, AccessSecret> implements DDNSAccessSecretService {

}
