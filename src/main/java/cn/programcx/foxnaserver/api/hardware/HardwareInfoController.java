package cn.programcx.foxnaserver.api.hardware;

import cn.programcx.foxnaserver.common.Result;
import cn.programcx.foxnaserver.dto.hardware.HardwareInfoDTO;
import cn.programcx.foxnaserver.util.HardwareInfoUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/hardware/")
@Tag(name = "HardwareInfo", description = "文件目录信息相关接口")
public class HardwareInfoController {
    @GetMapping("/info")
    public ResponseEntity<Result<HardwareInfoDTO>> getHardwareInfo() {
        return ResponseEntity.ok(Result.ok(HardwareInfoUtil.collectHardwareInfo()));
    }
}
