package cn.programcx.foxnaserver.api.media;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.MediaInfoDTO;
import cn.programcx.foxnaserver.dto.media.SubtitleJobStatus;
import cn.programcx.foxnaserver.dto.media.SubtitleTranscodeTask;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.service.media.DecodeMediaService;
import cn.programcx.foxnaserver.service.media.MediaTokenService;
import cn.programcx.foxnaserver.service.media.RangeMediaService;
import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import cn.programcx.foxnaserver.service.media.VideoFingerprintService;
import cn.programcx.foxnaserver.util.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/file/media")
@Tag(name = "MediaServiceController", description = "提供流媒体相关的接口")
public class MediaServiceController {

    private final String tempDir = System.getProperty("user.dir") +
            File.separator + "temp" +
            File.separator + "foxnas" +
            File.separator + "transcode";

    @Autowired
    private RangeMediaService rangeMediaService;

    @Autowired
    private DecodeMediaService decodeMediaService;

    @Autowired
    private MediaTokenService mediaTokenService;

    @Autowired
    private VideoFingerprintService fingerprintService;

    @Autowired
    private TranscodeJobService transcodeJobService;
    
    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private  RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "验证通过，返回临时访问Token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "无效的访问Token，无法访问指定文件的媒体流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @Operation(
            summary = "验证文件访问权限，获取token",
            description = "验证用户对指定媒体文件的访问权限，并生成临时访问Token"
    )
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/validate")
    public ResponseEntity<String> validatePermission(@RequestParam("path") String path) {
       String token = mediaTokenService.generateToken(path);
        log.info("验证文件[{}]权限通过，生成临时访问Token: {}", path, token);
        return ResponseEntity.ok(token);
    }

    @Operation(
            summary = "获取媒体元数据",
            description = "获取指定媒体文件的元数据信息"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取媒体元数据"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "获取媒体元数据时发生错误")
    })
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/metadata")
    public ResponseEntity<MediaInfoDTO> getMetadata(@RequestParam("path") String path) {

        try{
            MediaInfoDTO mediaInfoDTO = decodeMediaService.getRangeMediaData(path);
            return ResponseEntity.ok().body(mediaInfoDTO);
        }
        catch(Exception e){
            log.error("获取媒体元数据时发生错误: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "获取视频流",
            description = "获取指定视频文件的媒体流，支持Range请求"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取视频流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "无效的访问Token，无法访问指定文件的媒体流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "处理视频流时发生错误")
    })
    @GetMapping("/video-stream")
    public ResponseEntity<?> getVideoStream(@RequestParam("path") String path,
                                            @RequestParam(value = "token", required = true) String token,
                                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                                            @RequestParam(value = "soundTrackIndex", required = false) Integer soundTrackIndex,
                                            @RequestParam(value = "videoTrackIndex", required = false) Integer videoTrackIndex,
                                            HttpServletResponse response) {
        if (!mediaTokenService.validateToken(token, path)) {
            return ResponseEntity.status(401).body("无效的访问Token，无法访问指定文件的媒体流。");
        }
        if (rangeMediaService == null) {
            log.error("rangeMediaService is null inside getVideoStream method");
            return ResponseEntity.status(500).body("Internal server error: RangeMediaService is null");
        }

        String extension = getFileExtension(path);

        try {
            File file = new File(path);
            long fileLength = file.length();

            long start = 0;
            long end = fileLength - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            }

            long contentLength = end - start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Type", "video/" + extension);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(contentLength));
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            rangeMediaService.getRangeMediaData(start, end, file, response.getOutputStream());
            return ResponseEntity.status(HttpServletResponse.SC_PARTIAL_CONTENT).build();
        } catch (Exception e) {
            log.error("处理视频流时发生错误: {}", e.getMessage());
            return ResponseEntity.status(500).body("处理视频流时发生错误： " + e.getMessage());
        }


    }

    @Operation(
            summary = "获取媒体文件类型",
            description = "根据文件路径获取媒体文件的类型（视频、音频或其他）"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取媒体文件类型"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "获取媒体文件类型时发生错误")
    })
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/media-type")
    public ResponseEntity<String> getMediaFileType(@RequestParam("path") String path) {
        try {
            String mediaType = decodeMediaService.detectMediaType(path);
            return ResponseEntity.ok(mediaType);
        } catch (Exception e) {
            log.error("获取媒体文件类型时发生错误: {}", e.getMessage());
            return ResponseEntity.status(500).body("获取媒体文件类型时发生错误: " + e.getMessage());
        }
    }

    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功延长媒体访问Token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "无效的访问Token，无法延长指定文件的媒体访问权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "延长媒体访问Token时发生错误")
    })
    @Operation(
            summary = "延长媒体访问Token",
            description = "延长指定媒体文件的访问Token有效期"
    )
    @PostMapping("/prolong-token")
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    public void prolongMediaToken(@RequestParam("path") String path,
                                                     @RequestParam(value = "token", required = true) String token) {
        mediaTokenService.prolongToken(token, path);
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) return "";
        return fileName.substring(dotIndex + 1).toLowerCase();
    }


    /*===================================以下是提交转码部分===============================*/
    
    /**
     * 检查视频指纹，判断是否需要转码
     * 如果返回的 existed 为 true，表示已有可用的转码结果，直接使用返回的 jobId 播放
     * 如果返回的 existed 为 false，需要调用 /transcode/submit 提交转码任务
     */
    @Operation(
            summary = "检查视频指纹",
            description = "检查视频文件是否已转码过，避免重复转码。如果返回 existed=true，直接使用 jobId 播放；否则需要调用 /transcode/submit"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功检查指纹状态")
    })
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/transcode/check-fingerprint")
    public ResponseEntity<FingerprintCheckResponse> checkFingerprint(@RequestParam("path") String path) {
        try {
            String fingerprint = fingerprintService.generateFingerprint(path);
            String existingJobId = fingerprintService.getExistingJobId(fingerprint);
            
            if (existingJobId != null) {
                log.info("指纹 [{}] 已存在，复用转码任务 [{}]", fingerprint, existingJobId);
                return ResponseEntity.ok(new FingerprintCheckResponse(true, existingJobId, fingerprint,
                    "/api/file/media/stream/" + existingJobId + "/playlist.m3u8"));
            }
            
            // 检查是否有进行中的任务
            String processingJobId = fingerprintService.getProcessingJobId(fingerprint);
            if (processingJobId != null) {
                log.info("指纹 [{}] 有正在进行的任务 [{}]", fingerprint, processingJobId);
                return ResponseEntity.ok(new FingerprintCheckResponse(false, processingJobId, fingerprint, null));
            }
            
            log.info("指纹 [{}] 不存在，需要新建转码任务", fingerprint);
            return ResponseEntity.ok(new FingerprintCheckResponse(false, null, fingerprint, null));
        } catch (Exception e) {
            log.error("检查指纹时发生错误: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "提交视频转码任务",
            description = "提交MKV视频转码任务，支持指纹绑定避免重复转码，同时写入数据库"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功提交转码任务")
    })
    @PostMapping("/transcode/submit")
//    @CheckFilePermission(type = "Read", paramFields = {"path"})
    public ResponseEntity<Map<String, String>> submit(@RequestBody TranscodeRequest req) {
        // 获取当前用户ID
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录或登录已过期"));
        }

        // 生成或复用指纹
        String fingerprint;
        if (req.getFingerprint() != null && !req.getFingerprint().isEmpty()) {
            fingerprint = req.getFingerprint();
        } else {
            fingerprint = fingerprintService.generateFingerprint(req.getPath());
        }
        
        // 先检查数据库中是否有已完成的任务
        TranscodeJob completedJob = transcodeJobService.findCompletedByFingerprint(fingerprint);
        if (completedJob != null && completedJob.getCreatorId().equals(userId)) {
//            log.info("用户 [{}] 提交任务时发现指纹 [{}] 已存在已完成任务 [{}]，复用",
//                userId, fingerprint, completedJob.getJobId());
//            return ResponseEntity.ok(Map.of(
//                "jobId", completedJob.getJobId(),
//                "fingerprint", fingerprint,
//                "reused", "true",
//                "hlsPath", completedJob.getHlsPath() != null ? completedJob.getHlsPath() : "/api/file/media/stream/" + completedJob.getJobId() + "/playlist.m3u8"
//            ));
            transcodeJobService.deleteJob(completedJob.getJobId(), userId);
        }
        
        // 检查Redis中是否有已完成的转码（双重检查）
        String existingJobId = fingerprintService.getExistingJobId(fingerprint);
        if (existingJobId != null) {
//            log.info("任务提交时发现指纹 [{}] 已存在，复用任务 [{}]", fingerprint, existingJobId);
//            return ResponseEntity.ok(Map.of(
//                "jobId", existingJobId,
//                "fingerprint", fingerprint,
//                "reused", "true",
//                "hlsPath", "/api/file/media/stream/" + existingJobId + "/playlist.m3u8"
//            ));
            transcodeJobService.deleteJob(existingJobId, userId);
        }

        // 使用 TranscodeJobService 创建任务（包含数据库记录和消息发送）
        TranscodeJob job = transcodeJobService.createVideoJob(
            userId,
            req.getPath(),
            req.getAudioTrackIndex(),
            req.getSubtitleTrackIndex(),
            req.isImmediate(),
            fingerprint, req.getExpireSecs()
        );

        boolean isReused = !job.getJobId().equals(req.getFingerprint()) && 
                          (TranscodeJob.Status.COMPLETED.name().equals(job.getStatus()) ||
                           TranscodeJob.Status.PENDING.name().equals(job.getStatus()) ||
                           TranscodeJob.Status.PROCESSING.name().equals(job.getStatus()));

        return ResponseEntity.ok(Map.of(
            "jobId", job.getJobId(),
            "fingerprint", fingerprint,
            "reused", String.valueOf(isReused),
            "status", job.getStatus()
        ));
    }


    
    /**
     * 获取当前用户ID (UUID string)
     */
    private String getCurrentUserId() {
        String uuid = JwtUtil.getCurrentUuid();
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        return uuid;
    }

    // 查询转码状态（前端轮询）
    @Operation(
            summary = "查询视频转码状态",
            description = "前端轮询查询视频转码任务状态"
    )
    @GetMapping("/transcode/status/{jobId}")
    public ResponseEntity<JobStatus> status(@PathVariable String jobId) {
        JobStatus status = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }


    // 获取HLS文件
    @GetMapping("/stream/{jobId}/playlist.m3u8")
    public ResponseEntity<Resource> getHLS(@PathVariable String jobId) throws IOException {
        Path hls = Path.of(tempDir, jobId, "playlist.m3u8");
        return serveFile(hls, "application/x-mpegURL");
    }

    // 获取字幕
    @GetMapping("/stream/{jobId}/subtitle_{trackIndex}.vtt")
    public ResponseEntity<String> getSubtitle(@PathVariable String jobId, @PathVariable int trackIndex) throws IOException {
        Path vtt = Path.of(tempDir, jobId, "subtitle_" + trackIndex + ".vtt");
        String content = Files.readString(vtt);
        return ResponseEntity.ok()
                .header("Content-Type", "text/vtt")
                .body(content);
    }

    // 获取分片（支持Range请求，拖动进度条关键）
    @GetMapping("/stream/{jobId}/{filename:.+}")
    public ResponseEntity<Resource> getSegment(
            @PathVariable String jobId,
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String range) throws IOException {

        Path file = Path.of(tempDir, jobId, filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = Files.size(file);
        Resource resource = new InputStreamResource(Files.newInputStream(file));

        // 处理HTTP 206 Partial Content
        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isEmpty() ?
                    Long.parseLong(parts[1]) : fileSize - 1;

            long length = end - start + 1;

            // 使用 FileChannel 精确读取指定范围的字节，避免 BoundedInputStream 的大小限制
            byte[] data = new byte[(int) length];
            try (var raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
                raf.seek(start);
                raf.readFully(data);
            }

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header("Content-Type", "video/mp2t")
                    .header("Content-Length", String.valueOf(length))
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                    .header("Accept-Ranges", "bytes")
                    .body(new InputStreamResource(new java.io.ByteArrayInputStream(data)));
        }

        return ResponseEntity.ok()
                .header("Content-Type", "video/mp2t")
                .header("Accept-Ranges", "bytes")
                .body(resource);
    }

    /*===================================以下是字幕转码部分===============================*/

    /**
     * 提交字幕转码任务
     * 将指定视频的字幕轨道转换为VTT格式
     */
    @Operation(
            summary = "提交字幕转码任务",
            description = "将视频的字幕轨道转换为VTT格式，支持前端随时切换字幕"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功提交字幕转码任务")
    })
    @PostMapping("/subtitle/submit")
    public ResponseEntity<?> submitSubtitleTask(@RequestBody SubtitleTranscodeRequest req) {
        // 手动权限检查 - 验证用户是否有权限访问该路径
        if (!checkFilePermission(req.getPath(), "Read")) {
            return ResponseEntity.status(403).body(Map.of("error", "没有权限访问该文件"));
        }
        
        String jobId = UUID.randomUUID().toString();
        String outputPath = tempDir + "/subtitle_" + jobId + ".vtt";

        SubtitleTranscodeTask task = SubtitleTranscodeTask.builder()
                .jobId(jobId)
                .videoPath(req.getPath())
                .subtitleTrackIndex(req.getSubtitleTrackIndex())
                .outputPath(outputPath)
                .retryCount(0)
                .build();

        // 初始化字幕转码状态到Redis（TTL=8小时）
        SubtitleJobStatus status = new SubtitleJobStatus();
        status.setState(SubtitleJobStatus.State.PENDING);
        status.setCreateTime(LocalDateTime.now());
        redisTemplate.opsForValue().set(
                "subtitle_job:" + jobId, status, 8, TimeUnit.HOURS);

        // 发送到字幕转码队列
        rabbitTemplate.convertAndSend(
                TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE,
                TranscodeRabbitMQConfig.ROUTING_SUBTITLE,
                task
        );

        log.info("提交字幕转码任务 [{}]，视频 [{}]，字幕轨道 [{}]", 
            jobId, req.getPath(), req.getSubtitleTrackIndex());

        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    /**
     * 查询字幕转码状态
     */
    @Operation(
            summary = "查询字幕转码状态",
            description = "前端轮询查询字幕转码任务状态"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取字幕转码状态")
    })
    @GetMapping("/subtitle/status/{jobId}")
    public ResponseEntity<SubtitleJobStatus> getSubtitleStatus(@PathVariable String jobId) {
        SubtitleJobStatus status = (SubtitleJobStatus) redisTemplate.opsForValue().get("subtitle_job:" + jobId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    /**
     * 获取字幕VTT文件
     */
    @Operation(
            summary = "获取字幕VTT文件",
            description = "获取转码后的VTT格式字幕文件"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取字幕文件"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "字幕文件不存在")
    })
    @GetMapping("/subtitle/{jobId}")
    public ResponseEntity<String> getSubtitleVtt(@PathVariable String jobId) {
        try {
            Path vtt = Path.of(tempDir, "subtitle_" + jobId + ".vtt");
            if (!Files.exists(vtt)) {
                return ResponseEntity.notFound().build();
            }
            String content = Files.readString(vtt);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/vtt; charset=utf-8")
                    .header("Cache-Control", "public, max-age=86400")  // 缓存24小时
                    .body(content);
        } catch (IOException e) {
            log.error("读取字幕文件失败: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    private ResponseEntity<Resource> serveFile(Path path, String contentType) throws IOException {
        Resource res = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(res);
    }
    
    /**
     * 检查文件权限
     * @param path 文件路径
     * @param type 权限类型 (Read/Write/Delete)
     * @return 是否有权限
     */
    private boolean checkFilePermission(String path, String type) {
        String uuid = JwtUtil.getCurrentUuid();
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        try {
            java.nio.file.Path normalizedPath = java.nio.file.Path.of(path).normalize();
            
            // 检查路径注入
            if (normalizedPath.toString().contains("..")) {
                return false;
            }
            
            // 查询用户资源权限
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.programcx.foxnaserver.entity.Resource> queryWrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            queryWrapper.eq(cn.programcx.foxnaserver.entity.Resource::getOwnerUuid, uuid)
                       .eq(cn.programcx.foxnaserver.entity.Resource::getPermissionType, type);
            java.util.List<cn.programcx.foxnaserver.entity.Resource> resources = resourceMapper.selectList(queryWrapper);
            
            // 检查路径是否在允许的资源目录下
            for (cn.programcx.foxnaserver.entity.Resource resource : resources) {
                java.nio.file.Path allowedPath = java.nio.file.Path.of(resource.getFolderName()).normalize();
                if (normalizedPath.startsWith(allowedPath)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("检查文件权限时出错: {}", e.getMessage());
        }
        
        return false;
    }
}

@Data
class TranscodeRequest {
    private String path;
    private int audioTrackIndex = 0;
    private int subtitleTrackIndex = -1;
    private boolean immediate = false; // true=立即观看（高优先级）
    private String fingerprint; // 可选，如果前端已计算指纹可直接传入
    private Long expireSecs = 86400L; // 缓存过期时间，默认24小时
}

@Data
class FingerprintCheckResponse {
    private boolean existed;      // 是否已存在可用的转码结果
    private String jobId;         // 转码任务ID（existed=true时可用）
    private String fingerprint;   // 文件指纹
    private String hlsPath;       // HLS播放路径（existed=true时可用）

    public FingerprintCheckResponse(boolean existed, String jobId, String fingerprint, String hlsPath) {
        this.existed = existed;
        this.jobId = jobId;
        this.fingerprint = fingerprint;
        this.hlsPath = hlsPath;
    }
}

@Data
class SubtitleTranscodeRequest {
    private String path;
    private int subtitleTrackIndex = 0;
}
