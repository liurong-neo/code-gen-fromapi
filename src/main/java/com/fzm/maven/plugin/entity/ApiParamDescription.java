package com.fzm.maven.plugin.entity;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ApiParamDescription {

    private String flag;

    private String fileUpLoadTag;

    private Boolean required;

}
