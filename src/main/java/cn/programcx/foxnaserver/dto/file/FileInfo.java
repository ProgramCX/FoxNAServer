package cn.programcx.foxnaserver.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

import java.io.File;
import java.util.Map;
import java.util.Set;

@Slf4j
@Data
@Schema(description = "文件或目录信息")
public class FileInfo {
    @Schema(description = "路径")
    private String path;

    @Schema(description = "大小（字节）")
    private long size;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "最后修改时间（时间戳）")
    private long lastModified;

    @Schema(description = "类型，directory 或 file")
    private String type;

    @Schema(description = "mime 类型")
    private String mime;

    @Schema(description = "类别")
    private String category;

    @Schema(description = "是否可以直接播放")
    private boolean canPlay;

    @Schema(description = "是否需要转码")
    private boolean needTranscode;

    // Tika 单例（线程安全）
    private static final MimeTypes TIKA = MimeTypes.getDefaultMimeTypes();

    // 浏览器原生支持的 MIME 类型
    private static final Set<String> NATIVE_TYPES = Set.of(
            // 视频
            "video/mp4", "video/webm", "video/ogg", "video/ogv",
            "video/m4v", "video/quicktime",
            // 音频
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg",
            "audio/opus", "audio/webm", "audio/flac", "audio/x-wav", "audio/mp4",
            // 图片
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/bmp", "image/svg+xml", "image/x-icon", "image/vnd.microsoft-icon",
            // 文本/Office
            "text/plain", "text/html", "text/css", "text/javascript", "text/markdown",
            "application/json", "application/xml", "application/pdf",
            "application/javascript", "application/ecmascript", "application/typescript"
    );

    // 手动兜底映射表（确保常用类型准确，防止 Tika 返回 null）
    // 注意：每个扩展名只保留一个，重复会报错
    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            // 图片
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            // 视频（注意：ts 是 TypeScript，不是 video/mp2t，避免重复 key）
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4v", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("wmv", "video/x-ms-wmv"),
            // 音频
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("flac", "audio/flac"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("oga", "audio/ogg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("aac", "audio/aac"),
            Map.entry("wma", "audio/x-ms-wma"),
            Map.entry("opus", "audio/opus"),
            // 文档
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            // 代码
            Map.entry("ts", "application/typescript"),
            Map.entry("java", "text/x-java-source"),
            Map.entry("py", "text/x-python"),
            Map.entry("c", "text/x-csrc"),
            Map.entry("cpp", "text/x-c++src"),
            Map.entry("h", "text/x-chdr"),
            Map.entry("hpp", "text/x-c++hdr"),
            Map.entry("go", "text/x-go"),
            Map.entry("rs", "text/x-rust"),
            Map.entry("vue", "text/x-vue"),
            Map.entry("sql", "application/sql"),
            // 压缩包
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("bz2", "application/x-bzip2"),
            Map.entry("xz", "application/x-xz")
    );

    public static FileInfo of(File file) {
        FileInfo info = new FileInfo();
        String name = file.getName();
        info.setName(name);
        info.setPath(file.getPath().replace(File.separatorChar, '/'));
        info.setSize(file.length());
        info.setLastModified(file.lastModified());
        info.setType(file.isDirectory() ? "directory" : "file");

        // 目录直接返回
        if (file.isDirectory()) {
            info.setMime("inode/directory");
            info.setCategory("directory");
            info.setCanPlay(false);
            info.setNeedTranscode(false);
            return info;
        }

        // 提取扩展名（小写，不带点）
        String ext = "";
        int lastDot = name.lastIndexOf(".");
        if (lastDot != -1 && lastDot < name.length() - 1) {
            ext = name.substring(lastDot + 1).toLowerCase();
        }

        // 检测 MIME 类型
        String mime = detectMime(ext, name);

        // 分类
        String category = classify(mime, ext);

        // 判断是否可直接播放
        boolean canPlay = NATIVE_TYPES.contains(mime.toLowerCase());

        // 只有视频不能播放时才需要转码
        boolean needTranscode = category.equals("video") && !canPlay;

        info.setMime(mime);
        info.setCategory(category);
        info.setCanPlay(canPlay);
        info.setNeedTranscode(needTranscode);

        log.debug("FileInfo: name={}, mime={}, cat={}, canPlay={}",
                name, mime, category, canPlay);

        return info;
    }

    private static String detectMime(String ext, String fullName) {
        // 第1层：手动兜底表（最可靠）
        if (!ext.isEmpty() && EXT_TO_MIME.containsKey(ext)) {
            return EXT_TO_MIME.get(ext);
        }

        // 第2层：Tika 带点号扩展名
        if (!ext.isEmpty()) {
            try {
                MimeType mimeType = TIKA.forName("." + ext);
                if (mimeType != null) {
                    return mimeType.getName();
                }
            } catch (MimeTypeException ignored) {
            }
        }

        // 第3层：Tika 完整文件名
        try {
            MimeType mimeType = TIKA.forName(fullName);
            if (mimeType != null) {
                return mimeType.getName();
            }
        } catch (MimeTypeException e) {
            log.warn("Tika failed for [{}]: {}", fullName, e.getMessage());
        }

        return "application/octet-stream";
    }

    private static String classify(String mime, String ext) {
        String mimeLower = mime.toLowerCase();

        if (mimeLower.startsWith("video")) {
            return "video";
        } else if (mimeLower.startsWith("audio")) {
            return "audio";
        } else if (mimeLower.startsWith("image")) {
            return "image";
        } else if (mimeLower.startsWith("text") ||
                mimeLower.contains("pdf") ||
                mimeLower.contains("document") ||
                mimeLower.contains("msword") ||
                mimeLower.contains("excel") ||
                mimeLower.contains("powerpoint") ||
                mimeLower.contains("opendocument")) {
            return "doc";
        } else if (mimeLower.contains("zip") ||
                mimeLower.contains("compressed") ||
                mimeLower.contains("archive") ||
                ext.matches("zip|rar|7z|tar|gz|bz2|xz")) {
            return "archive";
        } else if (mimeLower.contains("javascript") ||
                mimeLower.contains("json") ||
                mimeLower.contains("xml") ||
                mimeLower.contains("html") ||
                mimeLower.contains("typescript") ||
                ext.matches("java|py|js|c|cpp|h|hpp|go|rs|vue|sql|yaml|yml|sh|bat|ps1")) {
            return "code";
        } else {
            return "other";
        }
    }
}