package cn.programcx.foxnaserver.dto.media;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 清理任务 DTO
 * 用于延迟清理转码产生的临时文件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupTask implements Serializable {
    
    private String jobId;
    private String outputDir;
    private String fingerprint;  // 用于清理时删除指纹绑定
}
