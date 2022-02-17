package com.fzm.maven.plugin.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CodeGenCfgEntity {

    private String modelPackage;

    private String modelTemplate;

    private String apiPackage;

    private String apiTemplate;

    private String serviceName;

    private String servicePackage;

    private String serviceTemplate;

    private String feignPackage;

    private String feignTemplate;

    private String feignFallBackPackage;

    private String feignFallBackTemplate;

    private String outputBasePath;

    private String sdkoutputBasePath;

    private String parentMvnArtifactId;

    private String projectVersion;
    
}
