package com.fzm.maven.plugin;

import com.alibaba.fastjson.JSONObject;
import com.fzm.maven.plugin.constant.CodeGenConstant;
import com.fzm.maven.plugin.constant.PropertyKeys;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Mojo(name = "api2fox")
public class Api2FoxMojo extends AbstractMojo {

    public static void main(String[] args) throws Exception {
        new Api2FoxMojo().execute();
    }
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("---------从标准API格式转换为apifox格式------------");
        Yaml yaml = new Yaml();
        Map<String, Object> apiObj;
        try {
            apiObj = yaml.load(new FileReader(CodeGenConstant.API_FILE_PATH));
        } catch (FileNotFoundException e) {
            getLog().error(e);
            return;
        }

        Map<String, Object> pathMap = (Map<String, Object>)apiObj.get("paths");
        pathMap.entrySet().stream().forEach(entry -> {
            getLog().info("process path "+entry.getKey());
            processPath(entry.getValue());
        });
        if(new File(CodeGenConstant.APIFOX_FILE_PATH).exists() && new File(CodeGenConstant.APIFOX_FILE_PATH).lastModified() > new File(CodeGenConstant.API_FILE_PATH).lastModified()){
            System.out.println("！！！检测到 apifox.json 文件已经存在，且当前操作将会覆盖此文件内容，请确认操作！！！");
            System.out.print("完整输入yes确认执行操作，其它任何输入都将忽略当前操作：");
            Scanner scan = new Scanner(System.in);
            if(scan.hasNextLine()){
                String in = scan.nextLine();
                if(!StringUtils.equalsIgnoreCase("yes", in)){
                    System.out.println("忽略退出");
                    return;
                }
            }
        }
        getLog().info("写入 apifox.json");
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        try (FileWriter writer = new FileWriter("src/main/resources/apifox.json")){
            gson.toJson(apiObj,writer);
        } catch (IOException e) {
            getLog().error(e);
        }
    }

    private void processPath(Object path){
        Map<String, Object> pathMap = (Map<String, Object>)path;
        pathMap.entrySet().stream().forEach(entry -> {
            getLog().info("process method "+entry.getKey());
            processMethod(entry.getValue());
        });
    }

    private void processMethod(Object method){
        Map<String, Object> methodMap = (Map<String, Object>)method;
        String baasRefResources = (String)methodMap.getOrDefault(PropertyKeys.API_REF_RESOURCES_KEY,null);
        String baasRefApis = (String)methodMap.getOrDefault(PropertyKeys.DEPENDENCY_API_DES_KEY,null);
        String baasRoles = (String)methodMap.getOrDefault(PropertyKeys.API_ROLES_KEY,null);

        String description = (String)methodMap.getOrDefault("description", null);

        // 将扩展属性追加到描述信息最后
        Map<String, Object> extData = new HashMap<>();
        if(StringUtils.isNotEmpty(baasRefResources)){
            extData.put(PropertyKeys.API_REF_RESOURCES_KEY,baasRefResources);
        }
        if(StringUtils.isNotEmpty(baasRefApis)){
            extData.put(PropertyKeys.DEPENDENCY_API_DES_KEY,baasRefApis);
        }
        if(StringUtils.isNotEmpty(baasRoles)){
            extData.put(PropertyKeys.API_ROLES_KEY,baasRoles);
        }
        if(!extData.isEmpty()){
            JSONObject jsonObject = new JSONObject(extData);
            String jsonString = "$$"+jsonObject.toJSONString()+"$$";
            if(StringUtils.isEmpty(description)){
                methodMap.put("description",jsonString);
            }else{
                methodMap.put("description",description+jsonString);
            }
        }
    }
}
