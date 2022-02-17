package com.fzm.maven.plugin.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ModelPropEntity {

    private String paramName;

    private ModelParamDescEntity modelParamDesc;
}