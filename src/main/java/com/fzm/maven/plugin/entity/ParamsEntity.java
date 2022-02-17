package com.fzm.maven.plugin.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ParamsEntity {

    private ApiParamDescription pathParamTag;

    private ApiParamDescription queryParamTag;

    private ApiParamDescription bodyParamTag;

    private String hasRequestParams;

    private String paramName;

    private String paramType;

    private Boolean required;

    private String apiParamDes;
}
