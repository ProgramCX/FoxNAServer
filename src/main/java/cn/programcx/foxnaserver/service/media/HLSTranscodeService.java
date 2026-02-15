package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.FFmpegProcessManager;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class HLSTranscodeService {

    private final FFmpegProcessManager processManager;

    private final String tempDir = System.getProperty("java.io.tmpdir") +
            File.separator + "foxnas" +
            File.separator + "transcode";
    private final RabbitTemplate rabbitTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 主方法：阻塞式转码流程
     * 1. 创建独立工作目录（UUID防并发冲突）
     * 2. 提取音频（原子写入）
     * 3. 提取字幕（可选）
     * 4. 封装DASH
     * 5. 清理临时文件（无论成功与否）
     */
    public void transcode(TranscodeTask transcodeTask, int secondsTimeout) throws Exception {
        // 使用UUID作为工作目录名
        String workId = transcodeTask.getJobId();
        Path outputPath = Path.of(tempDir, workId);

        long totalMills = getVideoDurationMillis(transcodeTask.getVideoPath());
        log.info("[HLSTranscodeService]Total mills to transcode to: {}", totalMills);

        try {
            Files.createDirectories(outputPath);
            log.info("开始转码任务 [{}]，工作目录：{}", transcodeTask.getJobId(), outputPath);

            Path audio = null;
            // 如果存在音轨，先提取（阻塞式，确保完成）
            if (transcodeTask.getAudioTrackIndex() >= 0) {
                audio = extractAudio(transcodeTask, secondsTimeout, outputPath, totalMills);
                log.info("音频提取完成：{}", audio);
            }

            Path subtitle = null;
            if (transcodeTask.getSubtitleTrackIndex() >= 0) {
                subtitle = extractSubtitle(transcodeTask, secondsTimeout, outputPath);
                log.info("字幕提取完成：{}", subtitle);
            }


            createHLS(transcodeTask, secondsTimeout, audio, outputPath, totalMills);
            log.info("DASH封装完成，输出目录：{}", outputPath);

        } catch (Exception e) {
            log.error("转码任务 [{}] 失败：{}", transcodeTask.getJobId(), e.getMessage());
            throw e;

        } finally {
//            rabbitTemplate.convertAndSend(TranscodeRabbitMQConfig.QUEUE_CLEANUP, "task.normal");
            //TODO: 添加定时清理任务
        }

    }

    /**
     * 提取字幕（转换为VTT格式）
     * 注意：当前实现直接提取，未做格式转换（假设输入为文本字幕）
     */
    private Path extractSubtitle(TranscodeTask transcodeTask, int secondsTimeout, Path output) throws Exception {
        String fileName = String.format("subtitle_%d.vtt", transcodeTask.getSubtitleTrackIndex());
        Path outFile = output.resolve(fileName);

        List<String> cmd = Arrays.asList(
                "ffmpeg", "-y",
                "-i", transcodeTask.getVideoPath(),
                "-map", "0:s:" + transcodeTask.getSubtitleTrackIndex(),
                outFile.toString()
        );

        processManager.execute(cmd, secondsTimeout, 0,null);
        return outFile;
    }

    private Path extractAudio(TranscodeTask transcodeTask, int secondsTimeout, Path output, long totalMills) throws Exception {
        String fileName = String.format("audio_track_%d.aac", transcodeTask.getAudioTrackIndex());
        Path tmpFile = output.resolve(fileName + ".tmp");
        Path finalFile = output.resolve(fileName);

        JobStatus status = (JobStatus) redisTemplate.opsForValue().get("job:" + transcodeTask.getJobId());

        if (status != null) {
            status.setStages(2);
            status.setCurrentStage(1);
        }

        try {
            Files.deleteIfExists(tmpFile);
            Files.deleteIfExists(finalFile);

            List<String> cmd = Arrays.asList(
                    "ffmpeg", "-y",
                    "-i", transcodeTask.getVideoPath(),
                    "-map", "0:a:" + transcodeTask.getAudioTrackIndex(),
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-ac", "2",          // 强制立体声
                    "-vn",
                    "-f", "adts",
                    "-progress", "pipe:1",
                    "-v", "quiet",
                    "-stats",
                    tmpFile.toString()
            );

            processManager.execute(cmd, secondsTimeout, totalMills, (total, current, progress) -> {
                if (status != null) {
                    status.setProgress(progress);
                    redisTemplate.opsForValue().set("job:" + transcodeTask.getJobId(), status);
                    log.info("音频提取进度：{}/{}（{}%）", current, total, progress);
                }
            });

            if (!Files.exists(tmpFile) || Files.size(tmpFile) < 1024) {
                throw new RuntimeException("音频提取失败：文件不存在或过小（<1KB）");
            }

            Files.move(tmpFile, finalFile, StandardCopyOption.ATOMIC_MOVE);

            try (FileChannel channel = FileChannel.open(finalFile, StandardOpenOption.READ)) {
                channel.force(true);
            }

            return finalFile;

        } catch (Exception e) {
            Files.deleteIfExists(tmpFile);
            Files.deleteIfExists(finalFile);
            log.error("提取音频失败：{}", e.getMessage());
            throw e;
        }
    }

    private void createHLS(TranscodeTask transcodeTask, int secondsTimeout,
                           Path audioPath, Path output, long totalMills) throws Exception {
        String m3u8Output = output.resolve("playlist.m3u8").toString();

        JobStatus status = (JobStatus) redisTemplate.opsForValue().get("job:" + transcodeTask.getJobId());

        List<String> cmd = new ArrayList<>();
        cmd.addAll(Arrays.asList("ffmpeg", "-hide_banner", "-y"));

        // 视频输入
        cmd.addAll(Arrays.asList("-i", transcodeTask.getVideoPath()));

        // 外部音频输入
        if (audioPath != null) {
            cmd.addAll(Arrays.asList("-i", audioPath.toString()));
        }

        // 视频复制并加标签
        cmd.addAll(Arrays.asList(
                "-map", "0:v:0",
                "-c:v", "copy",
                "-tag:v", "hvc1",    // 浏览器兼容 HEVC
                "-copyts",
                "-vsync", "cfr"
        ));

        // 音频处理
        if (audioPath != null) {
            cmd.addAll(Arrays.asList("-map", "1:a:0", "-c:a", "copy"));
            if (status != null) {
                status.setStages(2);
                status.setCurrentStage(2);
            }
        } else {
            cmd.addAll(Arrays.asList(
                    "-map", "0:a:" + transcodeTask.getAudioTrackIndex(),
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-ac", "2"        // 强制立体声
            ));
            if (status != null) {
                status.setStages(1);
                status.setCurrentStage(1);
            }
        }

        // HLS 分片参数
        cmd.addAll(Arrays.asList(
                "-f", "hls",
                "-hls_time", "3",
                "-hls_playlist_type", "event", // 边转码边播放
                "-hls_segment_filename", output.resolve("chunk-%05d.ts").toString(),
                "-hls_flags", "independent_segments",
                "-movflags", "+faststart",
                "-progress", "pipe:1",
                m3u8Output
        ));

        processManager.execute(cmd, secondsTimeout, totalMills, (total, current, progress) -> {
            if (status != null) {
                status.setProgress(progress);
                redisTemplate.opsForValue().set("job:" + transcodeTask.getJobId(), status);
                log.info("HLS 转码进度：{}/{}（{}%）", current, total, progress);
            }
        });
    }


    /**
     * 清理临时目录（递归删除）
     */
    private void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("无法删除临时文件 {}：{}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("清理目录失败 {}：{}", dir, e.getMessage());
        }
    }

    public long getVideoDurationMillis(String videoPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath
        );

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                throw new RuntimeException("无法获取视频时长");
            }

            // 返回的是秒（如 125.500000），转换为毫秒
            double seconds = Double.parseDouble(line.trim());
            return (long) (seconds * 1000);
        }
    }
}