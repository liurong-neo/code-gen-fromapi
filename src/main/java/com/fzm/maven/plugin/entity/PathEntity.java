package com.fzm.maven.plugin.entity;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PathEntity {

    private String apiPackage;

    private String servicePackage;

    private String modelPackage;

    private String feignPackage;

    private String feignFallBackPackage;

    private String className;

    private String fileName;   //版本号 . 资源名称

    private String classNameOnly; //不带Controller

    private String urlPath;

    private String serviceName;   //微服务名

    private String versionName;

    private String needImportModule;

    @Builder.Default
    private List<PathOperationEntity> pathGetEntityList = new ArrayList<>();
    @Builder.Default
    private List<PathOperationEntity> pathDeleteEntityList = new ArrayList<>();
    @Builder.Default
    private List<PathOperationEntity> pathPostEntityList = new ArrayList<>();
    @Builder.Default
    private List<PathOperationEntity> pathPutEntityList = new ArrayList<>();

    private Set<ImportClassEntity> importPackageList;
}
