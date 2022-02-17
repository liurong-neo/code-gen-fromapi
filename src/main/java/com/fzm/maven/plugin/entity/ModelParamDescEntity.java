package com.fzm.maven.plugin.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ModelParamDescEntity {

    private Boolean required;

    private String description;

    private String example;

    private String type;
}