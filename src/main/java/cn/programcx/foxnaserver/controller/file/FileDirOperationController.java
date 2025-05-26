package cn.programcx.foxnaserver.controller.file;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.util.LimitedInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@RestController
@RequestMapping("/api/file/op")
public class FileDirOperationController {
    @CheckFilePermission(type = "Write", bodyFields = {"paths"})
    @DeleteMapping("delete")
    public ResponseEntity<?> delete(@RequestBody List<String> paths) {
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
                    e.printStackTrace();
                    addFailedPath(failedPaths, path, e.getMessage());
                }
            } else {
                if (!dir.delete()) {
                    addFailedPath(failedPaths, path, "文件删除失败");
                }
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", paths.size() - failedPaths.size());
            resultMap.put("totalCount", paths.size());
            resultMap.put("failedPaths", failedPaths);
            return new ResponseEntity<>(resultMap, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        resultMap.put("status", "success");
        resultMap.put("totalDeleted", paths.size());

        return new ResponseEntity<>(resultMap, HttpStatus.OK);
    }

    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("get")
    public ResponseEntity<?> get(@RequestHeader(required = false) String Range, String path) throws IOException {
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

        return new ResponseEntity<>(resource, headers, Range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    @PutMapping("move")
    @CheckFilePermission(type = "Write", bodyFields = {"pathsList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    public ResponseEntity<?> move(@RequestBody List<Map<String, String>> pathsList) {
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
                    copyDirectory(sourcePath, targetPath, failedPaths);
                    deleteDirectoryRecursively(sourcePath);
                }
                successCount++;
            } catch (IOException e) {
                addFailedPath(failedPaths, List.of(oldPath, newPath), "移动失败：" + e.getMessage());
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", successCount);
            resultMap.put("failedCount",successCount- pathsList.size());
            resultMap.put("failedPaths", failedPaths);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        resultMap.put("totalMoved", successCount);
        return ResponseEntity.ok(resultMap);
    }


    @CheckFilePermission(type = "Write", bodyFields = {"pathList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    @PostMapping("copy")
    public ResponseEntity<?> copy(@RequestBody List<Map<String, String>> pathsList) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;


        for (Map<String, String> pathMap : pathsList) {

            String oldPath = pathMap.get("oldPath");
            String newPath = pathMap.get("newPath");
            Path sourcePath = Paths.get(oldPath);
            Path targetPath = Paths.get(newPath);

            if (!Files.exists(sourcePath)) {
                addFailedPath(failedPaths, List.of(oldPath, newPath), "源文件不存在！");
                continue;
            }

            try {
                if (Files.isRegularFile(sourcePath)) {

                    Files.copy(sourcePath, targetPath);

                } else {
                    copyDirectory(sourcePath, targetPath, failedPaths);
                }
                successCount++;
            } catch (IOException e) {
                addFailedPath(failedPaths, List.of(oldPath, newPath), "复制失败：" + e.getMessage());
            }


        }
        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("totalCount", pathsList.size());
            resultMap.put("failedCount", pathsList.size()-successCount);
            resultMap.put("failedPaths", failedPaths);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        resultMap.put("totalCopied", successCount);
        return ResponseEntity.ok(resultMap);
    }

    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PutMapping("rename")
    public ResponseEntity<?> rename(String path, String newName) {
        Map<String, Object> resultMap = new HashMap<>();
        File file = new File(path);
        boolean success = true;

        String parent = file.getParent();
        if (!file.exists()) {
            resultMap.put("message", "文件或目录不存在！");
            success = false;
        } else if (!file.renameTo(new File(parent, newName))) {
            resultMap.put("message", "重命名文件或目录失败！");
            success = false;
        }

        resultMap.put("path", path);
        resultMap.put("renameTo", newName);
        if (!success) {
            resultMap.put("status", "failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        return new ResponseEntity<>(resultMap,HttpStatus.OK);
    }

    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PostMapping("upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile[] file, @RequestBody String path) throws IOException {
        //上传文件
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;

        for(MultipartFile f : file) {
            if (f.isEmpty()) {
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
                addFailedPath(failedPaths, f.getOriginalFilename(), "文件上传失败！");
            }

        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("totalCount", file.length);
            resultMap.put("failedCount", file.length - successCount);
            resultMap.put("totalUpload", successCount);
            resultMap.put("failedPaths", failedPaths);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        return ResponseEntity.ok(resultMap);

    }

    private void copyDirectory(Path sourcePath, Path targetPath, List<Map<String, Object>> failedList) {
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
                    addFailedPath(failedList, List.of(sourcePath, targetPath), "复制失败：" + e.getMessage());
                }
            });
        } catch (IOException e) {
            addFailedPath(failedList, List.of(sourcePath, targetPath), "复制失败：" + e.getMessage());
        }
    }


    private void deleteDirectoryRecursively(Path path) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder()) // 先删子文件
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            //TODO: 日志记录删除失败
                        }
                    });
        } catch (IOException e) {
            //TODO: 日志记录删除失败
        }
    }

    private void addFailedPath(List<Map<String, Object>> failedPaths, Object path, String error) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", path);
        map.put("error", error);
        failedPaths.add(map);
    }
}
