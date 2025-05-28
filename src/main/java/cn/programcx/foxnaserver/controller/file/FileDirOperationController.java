package cn.programcx.foxnaserver.controller.file;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.service.ErrorLogService;
import cn.programcx.foxnaserver.util.JwtUtil;
import cn.programcx.foxnaserver.util.LimitedInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/file/op")
public class FileDirOperationController {
    @Autowired
    private ErrorLogService errorLogService;

    @CheckFilePermission(type = "Write", bodyFields = {"paths"})
    @DeleteMapping("delete")
    public ResponseEntity<?> delete(@RequestBody List<String> paths, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();

        List<Map<String, Object>> failedPaths = new ArrayList<>();

        for (String path : paths) {
            File dir = new File(path);

            if (!dir.exists()) {
                addFailedPath(failedPaths, path, "路径不存在");
                continue;
            }

            if (dir.isDirectory()) {
                Path pathDel = Paths.get(path);
                try {
                    Files.walkFileTree(pathDel, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                                throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    errorLogService.insertErrorLog(request, e, "遍历目录 " + pathDel + "失败！" + e.getMessage());
                    addFailedPath(failedPaths, path, e.getMessage());
                }
            } else {
                if (!dir.delete()) {

                    errorLogService.insertErrorLog(request, new Exception("删除文件 " + path + " 失败！"), "删除文件 " + path + " 失败！");

                    addFailedPath(failedPaths, path, "文件删除失败");
                }
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", paths.size() - failedPaths.size());
            resultMap.put("failedCount", failedPaths.size());
            resultMap.put("totalCount", paths.size());
            resultMap.put("failedPaths", failedPaths);

            log.error("[{}]删除文件或目录失败，路径数量: {}, 失败数量:{}, 请求的目录数组：{}, 出现错误的目录：{}，", JwtUtil.getCurrentUsername(),paths.size(),failedPaths.size(), paths,failedPaths);
            return new ResponseEntity<>(resultMap, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        resultMap.put("status", "success");
        resultMap.put("totalDeleted", paths.size());

        log.info("[{}]删除文件或目录成功，路径数量: {},目录数组：{}",JwtUtil.getCurrentUsername(), paths.size(), paths);
        return new ResponseEntity<>(resultMap, HttpStatus.OK);
    }

    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("get")
    public ResponseEntity<?> get(@RequestHeader(required = false) String Range, String path, HttpServletRequest request) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (file.isDirectory()) {
            //TODO: 压缩文件夹以便下载
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        long fileLength = file.length();
        long start = 0;
        long end = fileLength - 1;

        if (Range != null && Range.startsWith("bytes=")) {
            String[] ranges = Range.substring(6).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (start > end || end >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }

        long contentLength = end - start + 1;
        InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
        inputStream.skip(start);

        InputStreamResource resource = new InputStreamResource(new LimitedInputStream(inputStream, contentLength));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(contentLength);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20") + "\"");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);

        log.info("[{}]下载文件：{}, Range: {}, 起始字节: {}, 结束字节: {}, 文件长度: {}",JwtUtil.getCurrentUsername(), path, Range, start, end, fileLength);

        return new ResponseEntity<>(resource, headers, Range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    @PutMapping("move")
    @CheckFilePermission(type = "Write", bodyFields = {"pathsList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    public ResponseEntity<?> move(@RequestBody List<Map<String, String>> pathsList, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;

        for (Map<String, String> pathMap : pathsList) {
            String oldPath = pathMap.get("oldPath");
            String newPath = pathMap.get("newPath");

            if (oldPath == null || newPath == null) {
                addFailedPath(failedPaths, "", "oldPath 或 newPath 不能为空！");
                continue;
            }

            Path sourcePath = Paths.get(oldPath);
            Path targetPath = Paths.get(newPath);

            if (!Files.exists(sourcePath)) {
                addFailedPath(failedPaths, List.of(oldPath, newPath), "源文件不存在！");
                continue;
            }

            try {
                if (Files.isRegularFile(sourcePath)) {
                    // 是普通文件
                    Files.move(sourcePath, targetPath);
                } else {
                    // 是目录
                    copyDirectory(sourcePath, targetPath, failedPaths, request);
                    deleteDirectoryRecursively(sourcePath, request);
                }
                successCount++;
            } catch (IOException e) {
                errorLogService.insertErrorLog(request, e, "移动 " + sourcePath + " 到" + targetPath + " 失败：" + e.getMessage());
                addFailedPath(failedPaths, List.of(oldPath, newPath), "移动失败：" + e.getMessage());
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", successCount);
            resultMap.put("failedCount", pathsList.size() - successCount);
            resultMap.put("failedPaths", failedPaths);
            log.error("[{}]移动文件或目录失败，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUsername(), pathsList.size(), successCount, failedPaths.size(), pathsList);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        resultMap.put("totalMoved", successCount);

        log.info("[{}]移动文件或目录成功，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUsername(), pathsList.size(), successCount, failedPaths.size(), pathsList);
        return ResponseEntity.ok(resultMap);
    }


    @CheckFilePermission(type = "Write", bodyFields = {"pathsList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    @PostMapping("copy")
    public ResponseEntity<?> copy(@RequestBody List<Map<String, String>> pathsList, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;


        for (Map<String, String> pathMap : pathsList) {

            String oldPath = pathMap.get("oldPath");
            String newPath = pathMap.get("newPath");
            Path sourcePath = Paths.get(oldPath);
            Path targetPath = Paths.get(newPath);

            if (!Files.exists(sourcePath)) {
                errorLogService.insertErrorLog(request, new FileNotFoundException("源文件不存在！"), "源文件 " + sourcePath + " 不存在！");
                addFailedPath(failedPaths, List.of(oldPath, newPath), "源文件不存在！");
                continue;
            }

            try {
                if (Files.isRegularFile(sourcePath)) {

                    Files.copy(sourcePath, targetPath);

                } else {
                    copyDirectory(sourcePath, targetPath, failedPaths, request);
                }
                successCount++;
            } catch (IOException e) {
                errorLogService.insertErrorLog(request, e, "复制 " + sourcePath + " 到 " + targetPath + " 失败：" + e.getMessage());
                addFailedPath(failedPaths, List.of(oldPath, newPath), "复制失败：" + e.getMessage());
            }


        }
        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("totalCount", pathsList.size());
            resultMap.put("failedCount", pathsList.size() - successCount);
            resultMap.put("failedPaths", failedPaths);

            log.error("[{}]复制文件或目录失败，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUsername(), pathsList.size(), successCount, failedPaths.size(), pathsList);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        log.info("[{}]复制文件或目录成功，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUsername(), pathsList.size(), successCount, failedPaths.size(), pathsList);
        resultMap.put("status", "success");
        resultMap.put("totalCopied", successCount);
        return ResponseEntity.ok(resultMap);
    }

    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PutMapping("rename")
    public ResponseEntity<?> rename(String path, String newName, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        File file = new File(path);
        boolean success = true;

        String parent = file.getParent();
        if (!file.exists()) {
            errorLogService.insertErrorLog(request, new FileNotFoundException("文件或目录不存在！"), "重命名文件：文件或目录" + path + "不存在");
            resultMap.put("message", "文件或目录不存在！");
            success = false;
        } else if (!file.renameTo(new File(parent, newName))) {
            errorLogService.insertErrorLog(request, new IOException("重命名文件或目录失败！"), "文件或目录" + path + "重命名为"+newName+"失败");
            resultMap.put("message", "重命名文件或目录失败！");
            success = false;
        }

        resultMap.put("path", path);
        resultMap.put("renameTo", newName);
        if (!success) {
            resultMap.put("status", "failed");
            log.error("[{}]重命名文件或目录失败，路径: {}, 新名称: {}", JwtUtil.getCurrentUsername(), path, newName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        log.info("[{}]重命名文件或目录成功，路径: {}, 新名称: {}", JwtUtil.getCurrentUsername(), path, newName);
        resultMap.put("status", "success");
        return new ResponseEntity<>(resultMap, HttpStatus.OK);
    }

    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PostMapping("upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile[] file, @RequestParam String path, HttpServletRequest request) throws IOException {
        //上传文件
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;

        for (MultipartFile f : file) {
            if (f.isEmpty()) {
                errorLogService.insertErrorLog(request, new Exception("文件为空！"), "上传文件：文件为空");
                addFailedPath(failedPaths, f.getOriginalFilename(), "文件为空！");
                continue;
            }

            Path targetPath = Paths.get(path);
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            try {
                File destFile = new File(targetPath.toString(), Objects.requireNonNull(f.getOriginalFilename()));
                f.transferTo(destFile);
                successCount++;
            } catch (IOException e) {
                errorLogService.insertErrorLog(request, e, "上传文件" + f.getOriginalFilename() + "到" + path + "失败" + e.getMessage());
                addFailedPath(failedPaths, f.getOriginalFilename(), "文件上传失败！");
            }

        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("totalCount", file.length);
            resultMap.put("failedCount", file.length - successCount);
            resultMap.put("totalUpload", successCount);
            resultMap.put("failedPaths", failedPaths);

            log.error("[{}]上传文件失败，文件数量: {}, 成功数量: {}, 失败数量: {}, 文件数组：{}, 上传目录：{}", JwtUtil.getCurrentUsername(), file.length, successCount, failedPaths.size(), Arrays.toString(file),path);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        log.info("[{}]上传文件成功，文件数量: {}, 成功数量: {}, 失败数量: {}, 文件数组：{}, 上传目录：{}", JwtUtil.getCurrentUsername(), file.length, successCount, failedPaths.size(), Arrays.toString(file),path);
        resultMap.put("status", "success");
        return ResponseEntity.ok(resultMap);

    }

    private void copyDirectory(Path sourcePath, Path targetPath, List<Map<String, Object>> failedList, HttpServletRequest request) {
        if(targetPath.startsWith(sourcePath)) {
            addFailedPath(failedList, List.of(sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString()), "不能将目录复制到其自身或子目录中");
            errorLogService.insertErrorLog(request, new IOException("不能将目录复制到其自身或子目录中"), "复制目录" + sourcePath + "到" + targetPath + "失败：不能将目录复制到其自身或子目录中");
            return;
        }
        try {
            Files.walk(sourcePath).forEach(src -> {
                try {
                    Path relative = sourcePath.relativize(src);
                    Path dest = targetPath.resolve(relative);

                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest);
                    }
                } catch (IOException e) {
                    errorLogService.insertErrorLog(request, e, "复制文件" + sourcePath + "到" + targetPath + "失败:" + e.getMessage());

                    addFailedPath(failedList, List.of(sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString()), "复制失败：" + e.getMessage());
                }
            });
        } catch (IOException e) {
            errorLogService.insertErrorLog(request, e, "复制文件：遍历目录" + sourcePath + "失败:" + e.getMessage());
            addFailedPath(failedList, List.of(sourcePath, targetPath), "复制失败：" + e.getMessage());
        }
    }


    private void deleteDirectoryRecursively(Path path, HttpServletRequest request) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder()) // 先删子文件
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.error("[{}]删除目录失败，路径: {}, 错误信息: {}", JwtUtil.getCurrentUsername(), p, e.getMessage());
                            errorLogService.insertErrorLog(request, e, "删除目录" + path + "时" + "删除文件" + p + "失败：" + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("[{}]遍历目录失败，路径: {}, 错误信息: {}", JwtUtil.getCurrentUsername(), path, e.getMessage());
        }
    }

    private void addFailedPath(List<Map<String, Object>> failedPaths, Object path, String error) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", path);
        map.put("error", error);
        failedPaths.add(map);
    }
}
