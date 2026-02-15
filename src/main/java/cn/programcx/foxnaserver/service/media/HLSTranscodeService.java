package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.dto.media.FFmpegProcessManager;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
public class DASHTranscodeService {

    private final FFmpegProcessManager processManager;

    private final String tempDir = System.getProperty("java.io.tmpdir") +
            File.separator + "foxnas" +
            File.separator + "transcode";

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
        String workId = UUID.randomUUID().toString();
        Path outputPath = Path.of(tempDir, workId);

        try {
            Files.createDirectories(outputPath);
            log.info("开始转码任务 [{}]，工作目录：{}", transcodeTask.getJobId(), outputPath);

            Path audio = null;
            // 如果存在音轨，先提取（阻塞式，确保完成）
            if (transcodeTask.getAudioTrackIndex() >= 0) {
                audio = extractAudio(transcodeTask, secondsTimeout, outputPath);
                log.info("音频提取完成：{}", audio);
            }

            Path subtitle = null;
            if (transcodeTask.getSubtitleTrackIndex() >= 0) {
                subtitle = extractSubtitle(transcodeTask, secondsTimeout, outputPath);
                log.info("字幕提取完成：{}", subtitle);
            }


            createDASH(transcodeTask, secondsTimeout, audio, outputPath);
            log.info("DASH封装完成，输出目录：{}", outputPath);

        } catch (Exception e) {
            log.error("转码任务 [{}] 失败：{}", transcodeTask.getJobId(), e.getMessage());
            throw e;
        } finally {
             cleanupDirectory(outputPath);
        }
    }

    /**
     * 提取音频为AAC（ADTS格式）
     * 特点：原子写入 + 强制刷盘，确保createDASH读取时数据已完整落盘
     */
    private Path extractAudio(TranscodeTask transcodeTask, int secondsTimeout, Path output) throws Exception {
        String fileName = String.format("audio_track_%d.aac", transcodeTask.getAudioTrackIndex());
        Path tmpFile = output.resolve(fileName + ".tmp");
        Path finalFile = output.resolve(fileName);

        try {
            // 清理可能存在的旧临时文件
            Files.deleteIfExists(tmpFile);
            Files.deleteIfExists(finalFile); // 防止残留损坏文件

            List<String> cmd = Arrays.asList(
                    "ffmpeg", "-y",
                    "-i", transcodeTask.getVideoPath(),
                    "-map", "0:a:" + transcodeTask.getAudioTrackIndex(),
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-vn",                   // 不要视频
                    "-f", "adts",            // 明确指定ADTS格式
                    tmpFile.toString()
            );

            // 阻塞执行，直到FFmpeg完成或超时
            processManager.execute(cmd, secondsTimeout);

            if (!Files.exists(tmpFile) || Files.size(tmpFile) < 1024) {
                throw new RuntimeException("音频提取失败：文件不存在或过小（<1KB）");
            }

            // 原子重命名
            Files.move(tmpFile, finalFile, StandardCopyOption.ATOMIC_MOVE);

            // 强制刷盘，确保数据从OS缓存写入物理磁盘
            // 防止createDASH读取时读到缓存中的不完整数据
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

        processManager.execute(cmd, secondsTimeout);
        return outFile;
    }

    /**
     * 创建DASH流
     * @param audioPath 外部音频文件路径（可为null）
     * @param output 输出目录路径（DASH文件将生成在此目录）
     */
    private void createDASH(TranscodeTask transcodeTask, int secondsTimeout,
                            Path audioPath, Path output) throws Exception {
        String mpdOutput = output.resolve("manifest.mpd").toString();

        List<String> cmd = new ArrayList<>();
        cmd.addAll(Arrays.asList("ffmpeg", "-hide_banner", "-y"));

        // 输入1：原视频（视频流copy，不解码，CPU占用极低）
        cmd.addAll(Arrays.asList("-i", transcodeTask.getVideoPath()));

        // 输入2：外部提取的音频（如果存在）
        if (audioPath != null) {
            cmd.addAll(Arrays.asList("-i", audioPath.toString()));
        }

        // 视频映射：强制copy模式，不解码
        cmd.addAll(Arrays.asList(
                "-map", "0:v:0",
                "-c:v", "copy",
                "-copyts",              // 保持时间戳（防音画不同步）
                "-vsync", "cfr"         // 恒定帧率
        ));

        // 音频映射策略：
        // 1. 如果有外部音频（已提取的AAC），直接copy
        // 2. 否则从原视频映射指定音轨并转码为AAC
        if (audioPath != null) {
            cmd.addAll(Arrays.asList("-map", "1:a:0", "-c:a", "copy"));
        } else {
            cmd.addAll(Arrays.asList(
                    "-map", "0:a:" + transcodeTask.getAudioTrackIndex(),
                    "-c:a", "aac",
                    "-b:a", "192k"          // 转AAC确保浏览器兼容
            ));
        }

        // DASH关键参数：强制GOP对齐（每3秒一个关键帧）
        cmd.addAll(Arrays.asList(
                "-force_key_frames", "expr:gte(t,n_forced*3)",
                "-g", "72",                    // GOP=72帧（假设24fps，3秒）
                "-keyint_min", "72",
                "-sc_threshold", "0"           // 禁用场景切换关键帧
        ));

        // DASH封装参数
        cmd.addAll(Arrays.asList(
                "-f", "dash",
                "-seg_duration", "3",          // 3秒分片
                "-window_size", "0",           // 保留所有分片（点播模式）
                "-extra_window_size", "0",
                "-streaming", "0",             // 点播模式（非直播）
                "-movflags", "+faststart",
                "-init_seg_name", "init-$RepresentationID$.m4s",
                "-media_seg_name", "chunk-$RepresentationID$-$Number%05d$.m4s",
                mpdOutput
        ));

        // 阻塞执行DASH封装
        processManager.execute(cmd, secondsTimeout);
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
}