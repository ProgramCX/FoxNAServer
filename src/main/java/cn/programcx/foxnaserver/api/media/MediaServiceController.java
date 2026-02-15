package cn.programcx.foxnaserver.api.media;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.MediaInfoDTO;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import cn.programcx.foxnaserver.service.media.DecodeMediaService;
import cn.programcx.foxnaserver.service.media.MediaTokenService;
import cn.programcx.foxnaserver.service.media.RangeMediaService;
import com.nimbusds.jose.util.BoundedInputStream;
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
import java.io.InputStream;
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

    private final String tempDir = System.getProperty("java.io.tmpdir") +
            File.separator + "foxnas" +
            File.separator + "transcode";

    @Autowired
    private RangeMediaService rangeMediaService;

    @Autowired
    private DecodeMediaService decodeMediaService;

    @Autowired
    private MediaTokenService mediaTokenService;

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
    @PostMapping("/transcode/submit")
    public ResponseEntity<Map<String, String>> submit(
            @RequestBody TranscodeRequest req) {
        String jobId = UUID.randomUUID().toString();
        String outputDir = tempDir + "/" + jobId;

        TranscodeTask task = TranscodeTask.builder()
                .jobId(jobId)
                .videoPath(req.getPath())
                .audioTrackIndex(req.getAudioTrackIndex())
                .subtitleTrackIndex(req.getSubtitleTrackIndex())
                .outputDir(outputDir)
                .isImmediate(req.isImmediate())
                .retryCount(0)
                .build();

        // 初始化状态到Redis（TTL=8小时）
        JobStatus status = new JobStatus();
        status.setState(JobStatus.State.PENDING);
        status.setCreateTime(LocalDateTime.now());
        redisTemplate.opsForValue().set(
                "job:" + jobId, status, 8, TimeUnit.HOURS);

        // 根据immediate决定路由键
        String routingKey = req.isImmediate() ? "task.priority" : "task.normal";

        rabbitTemplate.convertAndSend(
                TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE,
                routingKey,
                task,
                msg -> {
                    if (req.isImmediate()) {
                        msg.getMessageProperties().setPriority(9);
                    }
                    return msg;
                }
        );

        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    // 查询转码状态（前端轮询）
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
    @GetMapping("/stream/{jobId}/subtitle.vtt")
    public ResponseEntity<String> getSubtitle(@PathVariable String jobId) throws IOException {
        Path vtt = Path.of(tempDir, jobId, "subtitle.vtt");
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
            InputStream is = Files.newInputStream(file);
            is.skip(start);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header("Content-Type", "video/mp2t")
                    .header("Content-Length", String.valueOf(length))
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                    .header("Accept-Ranges", "bytes")
                    .body(new InputStreamResource(new BoundedInputStream(is, length)));
        }

        return ResponseEntity.ok()
                .header("Content-Type", "video/mp2t")
                .header("Accept-Ranges", "bytes")
                .body(resource);
    }

    private ResponseEntity<Resource> serveFile(Path path, String contentType) throws IOException {
        Resource res = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(res);
    }
}

@Data
class TranscodeRequest {
    private String path;
    private int audioTrackIndex = 0;
    private int subtitleTrackIndex = -1;
    private boolean immediate = false; // true=立即观看（高优先级）
}