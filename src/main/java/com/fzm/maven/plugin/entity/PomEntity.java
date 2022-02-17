package com.fzm.maven.plugin.entity;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PomEntity {
    private String projectVersion;

    private String parentMvnArtifactId;
}
