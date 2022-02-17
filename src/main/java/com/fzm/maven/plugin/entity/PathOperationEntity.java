package com.fzm.maven.plugin.entity;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PathOperationEntity {

    private String methodName;

    @Builder.Default
    private List<ParamsEntity> params = new ArrayList<>();

    //增加标志，后续生成模板中据此判断是否是上传请求
    private String uploadRequest;

    private String responseType;

    private String urlPath;

    private String fullPath;



    //扩展属性等
    private String[] tags;

    private String summary;

    private String description;

    private String dependResources;

    private String roles;
}