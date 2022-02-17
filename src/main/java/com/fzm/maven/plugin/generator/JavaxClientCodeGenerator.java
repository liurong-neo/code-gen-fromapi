package com.fzm.maven.plugin.generator;

import com.fzm.maven.plugin.codegen.JavaxRsServerCodeGen;
import com.fzm.maven.plugin.constant.CodeGenConstant;
import com.fzm.maven.plugin.constant.DependecyHolder;
import com.fzm.maven.plugin.entity.ImportClassEntity;
import com.fzm.maven.plugin.entity.ModelClassEntity;
import com.fzm.maven.plugin.entity.PathEntity;
import com.fzm.maven.plugin.entity.ServiceEntity;
import io.swagger.models.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JavaxClientCodeGenerator extends JavaxServerCodeGenerator {

    private Map<String, OpenAPI> otherOpenApi;

    private Map<String, ServiceEntity> dependencyServiceMap = new HashMap<>();

    private Map<String, Map<String, List<String>>> dependenciesPathsWithServiceName;

    private Map<String, ModelClassEntity> otherModelMap = new ConcurrentHashMap<>(32);

    private final Map<String, PathEntity> dependencePathEntityMap = new ConcurrentHashMap<>(32);

    public void otherOpenApi(Map<String, OpenAPI> otherOpenApi){
        this.otherOpenApi = otherOpenApi;
    }

    public static Map<String, String> alreadyGenModelNameMap = new ConcurrentHashMap<>(16);


    public String currServiceAliasName = null;

    public void dependenciesPathsWithServiceName(Map<String, Map<String, List<String>>> dependenciesPaths){
        this.dependenciesPathsWithServiceName = dependenciesPaths;
    }

    public void dependencyServiceMap(Map<String, ServiceEntity> dependencyServiceMap ){
        this.dependencyServiceMap = dependencyServiceMap;
    }

    public void generateForFeign(){


        // 清理缓存
        this.clearCache();
        otherOpenApi.entrySet().stream().forEach(
                apiDep -> {
                    this.openAPI = apiDep.getValue();
                    this.currServiceAliasName = apiDep.getKey();
                    generateEntities();
                }
        );
        generate();
    }

    private void  generateEntities() {
        otherModelMap.clear();
        // 生成model类
        final Map<String, Schema> definitions = openAPI.getComponents().getSchemas();
        definitions.forEach(this::generateModel);

        Map<String, List<String>> dependenciesPaths = dependenciesPathsWithServiceName.get(currServiceAliasName);
        //根据path做粗过滤。
        apiImportClassList.clear();
        alreadyGenModelNameMap.clear();
        final Map<String, PathItem> paths = openAPI.getPaths().entrySet().stream().filter(pathEntry ->  dependenciesPaths.containsKey(pathEntry.getKey())).collect(Collectors.toMap((e) -> (String) e.getKey(), (e) -> e.getValue()));
        paths.entrySet().stream().forEach(
                entri -> generatePathEntity(entri.getKey(),entri.getValue(),currServiceAliasName)
        );
        List<String> dependecyImportModelName = new ArrayList<>(apiImportClassList.stream().filter(temp -> StringUtils.isNotBlank(temp.getClassName()) && !temp.getClassName().endsWith("*")).map(ent -> ent.getClassName().substring(ent.getClassName().lastIndexOf(".")+1)).collect(Collectors.toList()));
        //model类写到文件  根据当前 feign依赖的 model列表做过滤。同时 防止与本api文档中重复生成。
        otherModelMap.entrySet().stream().filter(modelEntity ->
                dependecyImportModelName.contains(modelEntity.getKey()) && !alreadyGenModelNameMap.containsKey(modelEntity.getKey())
        ).collect(Collectors.toMap((e) -> (String) e.getKey(), (e) -> e.getValue())).forEach(this::writeDependencyModelToFile);
    }

    @Override
    public List<File> generate() {
        // 生成Feign类
        this.dependencePathEntityMap.forEach((key, value) ->
                this.writeToFile(value, key + "Api", CodeGenConstant.FEIGN_TEMPLATE_FILE)
        );
        // 生成fallBack类
        this.dependencePathEntityMap.forEach((key, value) -> this.writeToFile(value, key + key.substring(0,key.indexOf("."))+"FeignFallBackFactory", CodeGenConstant.FEIGN_FALLBACK_TEMPLATE_FILE));
        return null;
    }


    private void clearCache() {
        dependencePathEntityMap.clear();
        apiImportClassList.clear();
    }


    private void generatePathEntity(String key, PathItem path, String serviceName) {
        List<String> depMethod = dependenciesPathsWithServiceName.get(serviceName).get(key);
        String versionName = getVersionName(key);
        //此处替换真实servcieName
        serviceName = getServiceRealName(serviceName);
        String className = getNameForStrWithDash(serviceName);     //合并同一个服务下的所有依赖接口到同一个文件。
        String fileName = versionName+"."+className;
        final JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        final String[] splitKeys = key.split("/\\{");
        PathEntity pathEntity = PathEntity.builder()
                .modelPackage(codeGenCfg.modelPackage()+"."+getNameForStrWithDash(serviceName).toLowerCase())
                .feignPackage(codeGenCfg.feignPackage()+"."+versionName)
                .feignFallBackPackage(codeGenCfg.feignFallBackPackage()+"."+versionName)
                .versionName(versionName)
                .className(className)
                .fileName(fileName)
                .urlPath(splitKeys[0])
                .serviceName(serviceName)
                .importPackageList(apiImportClassList)
                .build();
        if(depMethod.contains(HttpMethod.GET.toString())){
            pathEntity.getPathGetEntityList().add(getPathOperationProps(path.getGet(), key,key, HttpMethod.GET.toString()));
        }
        if(depMethod.contains(HttpMethod.DELETE.toString())){
            pathEntity.getPathDeleteEntityList().add(getPathOperationProps(path.getDelete(), key,key, HttpMethod.DELETE.toString()));
        }
        if(depMethod.contains(HttpMethod.POST.toString())){
            pathEntity.getPathPostEntityList().add(getPathOperationProps(path.getPost(), key,key, HttpMethod.POST.toString()));
        }
        if(depMethod.contains(HttpMethod.PUT.toString())){
            pathEntity.getPathPutEntityList().add(getPathOperationProps(path.getPut(), key,key, HttpMethod.PUT.toString()));
        }

        dependencePathEntityMap.merge(fileName, pathEntity, (oldValue, newValue) -> {
            // 合并importList
            Set<ImportClassEntity> oldImportSet = oldValue.getImportPackageList();
            newValue.getImportPackageList().addAll(oldImportSet);
            if (null != oldValue.getPathGetEntityList() && !oldValue.getPathGetEntityList().isEmpty()) {
                newValue.getPathGetEntityList().addAll(oldValue.getPathGetEntityList());
            }
            if (null != oldValue.getPathDeleteEntityList() && !oldValue.getPathDeleteEntityList().isEmpty()) {
                newValue.getPathDeleteEntityList().addAll(oldValue.getPathDeleteEntityList());
            }
            if (null != oldValue.getPathPostEntityList() && !oldValue.getPathPostEntityList().isEmpty()) {
                newValue.getPathPostEntityList().addAll(oldValue.getPathPostEntityList());
            }
            if (null != oldValue.getPathPutEntityList() && !oldValue.getPathPutEntityList().isEmpty()) {
                newValue.getPathPutEntityList().addAll(oldValue.getPathPutEntityList());
            }
            return newValue;
        });
        pathEntity.setNeedImportModule(apiImportClassList.size() > 0 ? "true" :null);
    }


    private String getServiceRealName(String serviceAliasName){
        String realName = serviceAliasName;
        if(dependencyServiceMap.containsKey(serviceAliasName)
                && StringUtils.isNotBlank(dependencyServiceMap.get(serviceAliasName).getRealName())){
            realName = dependencyServiceMap.get(serviceAliasName).getRealName();
        }
        return realName;
    }



    //第一次 只解析放入Map中。
    protected void processModel(Object context, String fileName, String templateName) {
        otherModelMap.put(fileName,(ModelClassEntity)context);
    }


    protected void writeDependencyModelToFile(String key, ModelClassEntity modelClassEntity) {
        if(alreadyGenModelNameMap.containsKey(key)){  //避免重复生成
            return;
        }
        //记录已经依赖的model
        String packName = getNameForStrWithDash(getServiceRealName(getServiceRealName(currServiceAliasName))).toLowerCase();
        modelClassEntity.setModelPackage(modelClassEntity.getModelPackage()+"."+packName);
        JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        alreadyGenModelNameMap.put(key,codeGenCfg.modelPackage()+"."+packName+"."+key);
        writeToFile(modelClassEntity,packName+"."+key, CodeGenConstant.MODEL_TEMPLATE_FILE);
        if(modelClassEntity.getRefModel() != null){
            //将model自身依赖的model 也要写入。
            otherModelMap.entrySet().stream().filter(modelEntity ->
                    modelClassEntity.getRefModel().contains(modelEntity.getKey()) && !alreadyGenModelNameMap.containsKey(modelEntity.getKey())
            ).collect(Collectors.toMap((e) -> (String) e.getKey(), (e) -> e.getValue())).forEach(this::writeDependencyModelToFile);
        }
    }

    @Override
    protected void getDependencyPath(io.swagger.v3.oas.models.Operation  operation){
    }
}