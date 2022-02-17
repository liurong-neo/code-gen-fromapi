package com.fzm.maven.plugin.entity;

import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ModelClassEntity {

    private String modelPackage;

    private String className;

    private Set<ImportClassEntity> importPackageList;

    private List<String> refModel;

    private List<ModelPropEntity> modelProps;
}