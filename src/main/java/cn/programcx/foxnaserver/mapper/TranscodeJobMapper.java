package cn.programcx.foxnaserver.mapper;

import cn.programcx.foxnaserver.entity.TranscodeJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 转码任务Mapper
 */
@Repository
public interface TranscodeJobMapper extends BaseMapper<TranscodeJob> {

    /**
     * 根据jobId查询任务
     */
    @Select("SELECT * FROM transcode_jobs WHERE job_id = #{jobId}")
    TranscodeJob selectByJobId(@Param("jobId") String jobId);

    /**
     * 根据创建者查询任务列表（分页）
     */
    @Select("SELECT * FROM transcode_jobs WHERE creator_id = #{creatorId} ORDER BY created_at DESC")
    IPage<TranscodeJob> selectByCreator(Page<TranscodeJob> page, @Param("creatorId") String creatorId);

    /**
     * 根据状态查询任务列表
     */
    @Select("SELECT * FROM transcode_jobs WHERE status = #{status} ORDER BY created_at DESC")
    List<TranscodeJob> selectByStatus(@Param("status") String status);

    /**
     * 根据指纹查询已完成的任务
     */
    @Select("SELECT * FROM transcode_jobs WHERE fingerprint = #{fingerprint} AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT 1")
    TranscodeJob selectCompletedByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 更新任务状态
     */
    @Update("UPDATE transcode_jobs SET status = #{status}, updated_at = NOW() WHERE job_id = #{jobId}")
    int updateStatus(@Param("jobId") String jobId, @Param("status") String status);

    /**
     * 更新任务进度
     */
    @Update("UPDATE transcode_jobs SET progress = #{progress}, current_stage = #{currentStage}, updated_at = NOW() WHERE job_id = #{jobId}")
    int updateProgress(@Param("jobId") String jobId, @Param("progress") Double progress, @Param("currentStage") Integer currentStage);

    /**
     * 更新任务为完成状态
     */
    @Update("UPDATE transcode_jobs SET status = 'COMPLETED', progress = 100, hls_path = #{hlsPath}, completed_at = NOW(), updated_at = NOW() WHERE job_id = #{jobId}")
    int updateCompleted(@Param("jobId") String jobId, @Param("hlsPath") String hlsPath);

    /**
     * 更新任务为失败状态
     */
    @Update("UPDATE transcode_jobs SET status = 'FAILED', error_message = #{errorMessage}, updated_at = NOW() WHERE job_id = #{jobId}")
    int updateFailed(@Param("jobId") String jobId, @Param("errorMessage") String errorMessage);

    /**
     * 更新重试次数
     */
    @Update("UPDATE transcode_jobs SET retry_count = retry_count + 1, updated_at = NOW() WHERE job_id = #{jobId}")
    int incrementRetryCount(@Param("jobId") String jobId);

    /**
     * 统计创建者的任务数量
     */
    @Select("SELECT COUNT(*) FROM transcode_jobs WHERE creator_id = #{creatorId}")
    Long countByCreator(@Param("creatorId") String creatorId);

    /**
     * 统计各状态任务数量
     */
    @Select("SELECT status, COUNT(*) as count FROM transcode_jobs WHERE creator_id = #{creatorId} GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus(@Param("creatorId") String creatorId);

    /**
     * 删除创建者的所有任务
     */
    @Update("DELETE FROM transcode_jobs WHERE creator_id = #{creatorId}")
    int deleteByCreator(@Param("creatorId") String creatorId);

    /**
     * 根据jobId删除任务
     */
    @Update("DELETE FROM transcode_jobs WHERE job_id = #{jobId} AND creator_id = #{creatorId}")
    int deleteByJobIdAndCreator(@Param("jobId") String jobId, @Param("creatorId") String creatorId);

    /**
     * 查询进行中的任务（用于系统重启后恢复）
     */
    @Select("SELECT * FROM transcode_jobs WHERE status IN ('PENDING', 'PROCESSING') ORDER BY created_at ASC")
    List<TranscodeJob> selectRunningJobs();

    /**
     * 根据指纹和创建者查询任务
     */
    @Select("SELECT * FROM transcode_jobs WHERE fingerprint = #{fingerprint} AND creator_id = #{creatorId} ORDER BY created_at DESC LIMIT 1")
    TranscodeJob selectByFingerprintAndCreator(@Param("fingerprint") String fingerprint, @Param("creatorId") String creatorId);
}
