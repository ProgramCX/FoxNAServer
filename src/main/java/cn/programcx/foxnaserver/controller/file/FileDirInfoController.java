package cn.programcx.foxnaserver.controller.file;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/file/info")
public class FileDirInfoController {

    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/getList")
    public ResponseEntity<?> getList(String path,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "200") int pageSize,
                                     @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
                                     @RequestParam(value = "order", defaultValue = "asc") String order) {
        File dir = new File(path);
        Map<String, Object> retMap = new HashMap<>();

        if (!dir.exists()) {
            return ResponseEntity.notFound().build();
        }
        if (!dir.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {

            retMap.put("list", Collections.emptyList());
            retMap.put("total", 0);
            retMap.put("from", 0);
            retMap.put("to", 0);
            retMap.put("page", page);
            retMap.put("pageSize", pageSize);
            return ResponseEntity.ok(retMap);
        }


        List<Map<String, Object>> list = new ArrayList<>();
        for (File file : files) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", file.getName());
            map.put("path", file.getPath().replace(File.separatorChar, '/'));
            map.put("size", file.length());
            map.put("lastModified", file.lastModified());
            map.put("type", file.isDirectory() ? "directory" : "file");
            list.add(map);
        }

        // 排序
        if (sortBy != null && !sortBy.isEmpty()) {
            boolean asc = "asc".equalsIgnoreCase(order);
            list.sort((m1, m2) -> {

                String type1 = (String) m1.get("type");
                String type2 = (String) m2.get("type");

                int typeCmp = compareFileType(type1, type2);
                if (typeCmp != 0) {
                    return typeCmp;
                }

                Object o1 = m1.get(sortBy);
                Object o2 = m2.get(sortBy);

                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return asc ? -1 : 1;
                if (o2 == null) return asc ? 1 : -1;

                int cmp;
                if (o1 instanceof String && o2 instanceof String) {
                    cmp = ((String) o1).compareToIgnoreCase((String) o2);
                } else if (o1 instanceof Number && o2 instanceof Number) {
                    cmp = Long.compare(((Number) o1).longValue(), ((Number) o2).longValue());
                } else if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    cmp = ((Comparable) o1).compareTo(o2);
                } else {
                    cmp = o1.toString().compareTo(o2.toString());
                }
                return asc ? cmp : -cmp;
            });
        }

        // 分页
        int total = list.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> pageList = list.subList(from, to);

        retMap.put("list", pageList);
        retMap.put("total", total);
        retMap.put("from", from);
        retMap.put("to", to);
        retMap.put("page", page);
        retMap.put("pageSize", pageSize);
        retMap.put("totalPage", total / pageSize + (total % pageSize == 0 ? 0 : 1));

        return ResponseEntity.ok(retMap);
    }


    private int compareFileType(String type1, String type2) {
        if (type1 == null && type2 == null) return 0;
        if (type1 == null) return 1;  // 空的排后面
        if (type2 == null) return -1;

        if (type1.equalsIgnoreCase(type2)) return 0;
        if ("directory".equalsIgnoreCase(type1)) return -1;
        if ("directory".equalsIgnoreCase(type2)) return 1;
        return type1.compareToIgnoreCase(type2); // fallback 字符串比较
    }
}
