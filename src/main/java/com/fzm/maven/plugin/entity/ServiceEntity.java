package com.fzm.maven.plugin.entity;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ServiceEntity {

    //别名
    private String aliasName;

    //真实名称
    private String realName;

    //版本
    private String version;

}
