package cn.programcx.foxnaserver.dto.media;

import cn.programcx.foxnaserver.callback.TranscodeCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FFmpegProcessManager {

    private final String tempDir = System.getProperty("java.io.tmpdir") +
            File.separator + "foxnas" +
            File.separator + "transcode";

    // 控制并发转码数量（根据R7 8845HS性能，2个并发比较合适）
    private final Semaphore semaphore = new Semaphore(2);

    // 线程池用于执行FFmpeg进程（命名线程便于调试）
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ffmpeg-worker-" + counter.incrementAndGet());
                    t.setDaemon(true); // 守护线程，不阻止JVM退出
                    return t;
                }
            }
    );

    /**
     * 阻塞式执行FFmpeg命令
     * 调用线程会阻塞直到FFmpeg完成、超时或异常
     *
     * @param command        FFmpeg命令参数列表
     * @param timeoutSeconds 最大执行时间（秒）
     * @throws Exception 执行失败、超时或退出码非0时抛出
     */
    public void execute(List<String> command, long timeoutSeconds, long totalMills, TranscodeCallback callback) throws Exception {
        // 1. 获取信号量（控制并发数，最多等待30秒）
        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("转码服务器繁忙，当前有 " + semaphore.getQueueLength() + " 个任务排队");
        }

        Future<?> future = null;
        try {
            // 2. 提交任务到线程池
            future = executor.submit(() -> {
                Process process = null;
                Thread logThread = null;

                try {
                    // 确保工作目录存在
                    Path tempDirPath = Paths.get(tempDir);
                    if (!Files.exists(tempDirPath)) {
                        Files.createDirectories(tempDirPath);
                    }

                    // 构建进程
                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(new File(tempDir));
                    pb.redirectErrorStream(true); // 合并stderr到stdout

                    // 设置环境变量（处理中文路径）
                    setupEnvironment(pb);

                    log.info("[FFmpeg] 启动: {}", String.join(" ", command));
                    process = pb.start();

                    // 3. 启动日志读取线程（守护线程，防止阻塞进程缓冲区）
                    final Process p = process;
                    logThread = new Thread(() -> readProcessOutput(p, totalMills, callback), "FFmpeg-Logger");
                    logThread.setDaemon(true);
                    logThread.start();

                    // 4. 等待进程完成（阻塞，但可被中断）
                    // 注意：这里不设置超时，由外层的 future.get 控制总超时时间
                    int exitCode = process.waitFor();

                    // 等待日志线程读取完毕（给1秒缓冲时间）
                    logThread.join(1000);

                    // 5. 检查退出码
//                    if (exitCode != 0) {
//                        log.error("[FFmpeg] 非正常退出，码: {}", exitCode);
//                        throw new RuntimeException("FFmpeg 执行失败，退出码: " + exitCode);
//                    }

                    log.info("[FFmpeg] 正常完成");

                } catch (InterruptedException e) {
                    // 线程被中断（通常是超时取消或外部中断）
                    log.warn("[FFmpeg] 任务被中断");
                    if (process != null) {
                        process.destroyForcibly();
                        // 等待进程真正结束（最多5秒）
                        try {
                            process.waitFor(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    // 重新设置中断标志，让上层知道被中断了
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("转码任务被中断", e);

                } catch (IOException e) {
                    log.error("[FFmpeg] IO错误: {}", e.getMessage());
                    if (process != null) {
                        process.destroyForcibly();
                    }
                    throw new RuntimeException("FFmpeg 启动失败: " + e.getMessage(), e);

                } finally {
                    // 6. 确保进程被销毁（如果还在运行）
                    if (process != null && process.isAlive()) {
                        log.warn("[FFmpeg] 强制终止残留进程");
                        process.destroyForcibly();
                    }
                }
            });

            // 7. 阻塞等待任务完成（单点超时控制）
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);

            } catch (TimeoutException e) {
                // 超时取消任务
                log.error("[FFmpeg] 执行超时（> {} 秒），强制取消", timeoutSeconds);
                future.cancel(true); // 发送中断信号给执行线程
                throw new TimeoutException("FFmpeg 执行超时（超过 " + timeoutSeconds + " 秒）");

            } catch (ExecutionException e) {
                // 任务执行中抛出异常
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                } else {
                    throw new RuntimeException(cause);
                }

            } catch (InterruptedException e) {
                // 调用者线程被中断（如Spring应用关闭）
                log.warn("[FFmpeg] 调用被中断");
                future.cancel(true);
                Thread.currentThread().interrupt(); // 恢复中断标志
                throw new RuntimeException("转码被外部中断", e);
            }

        } finally {
            // 8. 释放信号量（无论成功与否）
            semaphore.release();
        }
    }

    /**
     * 读取进程输出流（日志）
     * 必须在单独线程执行，防止缓冲区满导致死锁
     */
    private void readProcessOutput(Process process, long totalMills, TranscodeCallback callback) {
        // 自动检测编码：优先使用系统默认
        Charset charset = Charset.defaultCharset();

        // Windows 下如果默认编码是 GBK（旧版Java或旧版Windows），则使用 GBK
        // Java 18+ 在 Windows 上默认使用 UTF-8（如果系统设置正确）
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String jnuEncoding = System.getProperty("sun.jnu.encoding");
            if ("GBK".equalsIgnoreCase(jnuEncoding) || "MS936".equalsIgnoreCase(jnuEncoding)) {
                charset = Charset.forName("GBK");
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset))) {

            String line;
            long currentTimeMs = 0;
            while ((line = reader.readLine()) != null) {
                // 限制单行日志长度，防止极端情况内存溢出
                if (line.length() > 1000) {
                    line = line.substring(0, 1000) + "... [截断]";
                }
                log.debug("[FFmpeg] {}", line);

                if (line.startsWith("out_time_ms=")) {
                    // 注意：这个值实际是微秒，需要除以1000
                    long microseconds = Long.parseLong(line.substring(12));
                    currentTimeMs = microseconds / 1000;

                    // 计算百分比
                    int percent = (int) ((currentTimeMs * 100) / totalMills);

                    // 防止超100%（FFmpeg可能输出比实际时长多一点）
                    percent = Math.min(percent, 100);

                    // 回调通知
                    callback.onStatusCallback(totalMills, currentTimeMs, percent);
                }
                else if (line.equals("progress=end")) {
                    callback.onStatusCallback(totalMills, currentTimeMs, 100);
                    break;
                }
            }
        } catch (IOException e) {
            // 进程结束时流关闭会抛异常，这是正常的
            if (process.isAlive()) {
                log.error("[FFmpeg] 读取输出流失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 设置进程环境变量（处理编码）
     */
    private void setupEnvironment(ProcessBuilder pb) {
        // 强制使用 UTF-8 处理文件名（避免中文乱码）
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        // 非Windows系统设置UTF-8环境
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("LC_ALL", "en_US.UTF-8");
        }
    }
}