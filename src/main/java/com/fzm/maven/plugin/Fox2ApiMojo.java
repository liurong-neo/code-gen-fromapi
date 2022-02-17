package com.fzm.maven.plugin;

import com.fzm.maven.plugin.constant.CodeGenConstant;
import com.fzm.maven.plugin.constant.PropertyKeys;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.Scanner;

@Mojo(name = "fox2api")
public class Fox2ApiMojo extends AbstractMojo {
    // 检测到变动才需要写入
    private boolean need2Write;

    public static void main(String[] args) throws Exception {
        new Fox2ApiMojo().execute();
    }
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("---------从apifox格式转换为标准API格式------------");
        Map<?, ?> map;
        try (FileReader reader = new FileReader(CodeGenConstant.APIFOX_FILE_PATH)){
            Gson gson = new Gson();
            map = gson.fromJson(reader, Map.class);
        } catch (Exception e) {
           getLog().error(e);
           return;
        }

        Map<String, Object> pathMap = (Map<String, Object>)map.get("paths");
        pathMap.entrySet().stream().forEach(entry -> {
            getLog().info("process path "+entry.getKey());
            processPath(entry.getValue());
        });

        if(new File(CodeGenConstant.API_FILE_PATH).exists() && new File(CodeGenConstant.API_FILE_PATH).lastModified() > new File(CodeGenConstant.APIFOX_FILE_PATH).lastModified()){
            System.out.println("！！！检测到 api.yaml 文件已经存在且日期更新，当前操作将会覆盖此文件内容，请确认操作！！！");
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
        getLog().info("写入 api.yaml");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml();
        try(FileWriter writer = new FileWriter(CodeGenConstant.API_FILE_PATH)){
            String data = yaml.dumpAsMap(map);
            writer.write(data);
        }catch (Exception ex){
            getLog().error(ex);
            return;
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
        String description = (String)methodMap.getOrDefault(PropertyKeys.API_METHOD_DESCRIPTION, null);
        if(StringUtils.isEmpty(description) || description.indexOf("$$") == -1){
            return;
        }

        int idx1 = description.indexOf("$$");
        int idx2 = description.lastIndexOf("$$");
        if(idx2 == idx1){
            return;
        }

        // 将扩展属性追加到描述信息最后
        String jsonData = description.substring(idx1+2, idx2);
        Map<?,?> extData = new Gson().fromJson(jsonData, Map.class);
        if(extData.containsKey(PropertyKeys.API_REF_RESOURCES_KEY)){
            methodMap.put(PropertyKeys.API_REF_RESOURCES_KEY, extData.get(PropertyKeys.API_REF_RESOURCES_KEY));
            need2Write=true;
        }
        if(extData.containsKey(PropertyKeys.DEPENDENCY_API_DES_KEY)){
            methodMap.put(PropertyKeys.DEPENDENCY_API_DES_KEY, extData.get(PropertyKeys.DEPENDENCY_API_DES_KEY));
            need2Write=true;
        }
        if(extData.containsKey(PropertyKeys.API_ROLES_KEY)){
            methodMap.put(PropertyKeys.API_ROLES_KEY, extData.get(PropertyKeys.API_ROLES_KEY));
            need2Write=true;
        }

        if(need2Write){
            methodMap.put(PropertyKeys.API_METHOD_DESCRIPTION, description.substring(0, idx1));
        }
    }
}
