package cn.programcx.foxnaserver.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ResourceDTO {
    private String ownerUuid;
    private String folderName;
    private List<String> types;
}
