package cn.programcx.foxnaserver.controller.file;

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

    @GetMapping("/getList")
    public ResponseEntity<?> getList(String path,@RequestParam(required = false) String method, @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "50") int pageSize) {
        File dir = new File(path);
        Map<String,Object> retMap = new HashMap<>();

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        if (!dir.exists()) {
            return ResponseEntity.notFound().build();
        }
        if (!dir.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        File[] files = dir.listFiles();

        if(files==null||files.length==0){
            return ResponseEntity.internalServerError().build();
        }

        int total = files.length;
        int from = Math.min((page - 1) * pageSize,total);
        int to = Math.min(from + pageSize,total);

        File[] filesCut = Arrays.copyOfRange(files,from,to);

        for (File file : filesCut) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("name", file.getName());
            map.put("path", file.getPath().replace(File.separatorChar, '/'));
            map.put("size", file.length());
            map.put("lastModified", file.lastModified());
            map.put("type", file.isDirectory() ? "directory" : "file");

            list.add(map);
        }

        retMap.put("list", list);
        retMap.put("total", total);
        retMap.put("from", from);
        retMap.put("to", to);
        retMap.put("page", page);
        retMap.put("pageSize", pageSize);

        return ResponseEntity.ok(retMap);

    }
}
