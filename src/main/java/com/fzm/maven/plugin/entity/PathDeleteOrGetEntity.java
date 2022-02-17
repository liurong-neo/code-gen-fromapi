package com.fzm.maven.plugin.entity;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PathDeleteOrGetEntity {

    private String className;

    private String methodName;

    private List<ParamsEntity> params;

    private String responseType;
}
