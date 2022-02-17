package com.fzm.maven.plugin.service;

import com.fzm.maven.plugin.constant.PropertyKeys;
import com.fzm.maven.plugin.entity.ImportClassEntity;
import com.fzm.maven.plugin.entity.PathEntity;
import com.fzm.maven.plugin.entity.PathOperationEntity;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ApiFileReader {

    @Getter
    private String apiFilePath;

    public ApiFileReader(String apiFilePath){
        this.apiFilePath = apiFilePath;
    }

    private final Map<String, PathEntity> pathEntityMap = new ConcurrentHashMap<>(32);


    public Map<String, PathEntity> getPathApiDes() {
        pathEntityMap.clear();
        OpenAPI openAPI = null;
        try {
            openAPI = createOpenApi(getApiFilePath());
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        }
        final Paths paths =openAPI.getPaths();;
        paths.forEach(this::getPathEntity);
        return pathEntityMap;
    }


    private void getPathEntity(String key, PathItem path) {
        final PathEntity pathEntity = PathEntity.builder()
                .urlPath(key)
                .build();
        if(path.getGet() != null){
            pathEntity.getPathGetEntityList().add(getPathProps(path.getGet(), key));
        }
        if(path.getDelete() != null){
            pathEntity.getPathDeleteEntityList().add(getPathProps(path.getDelete(), key));
        }
        if(path.getPost() != null){
            pathEntity.getPathPostEntityList().add(getPathProps(path.getPost(), key));
        }
        if(path.getPut() != null){
            pathEntity.getPathPutEntityList().add(getPathProps(path.getPut(), key));
        }
        pathEntityMap.merge(key, pathEntity, (oldValue, newValue) -> {
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
    }

    protected PathOperationEntity getPathProps(io.swagger.v3.oas.models.Operation operation, String fullPath) {
        return Optional.ofNullable(operation)
                .map(opera ->
                        {
                            String roles = null;
                            String resources = null;
                            if(null != opera.getExtensions()){
                                Map<String,Object> extentions = opera.getExtensions();
                                roles = extentions.containsKey(PropertyKeys.API_ROLES_KEY) ? String.valueOf(extentions.get(PropertyKeys.API_ROLES_KEY)) : null;
                                resources = extentions.containsKey(PropertyKeys.API_REF_RESOURCES_KEY) ? String.valueOf(extentions.get(PropertyKeys.API_REF_RESOURCES_KEY)) : null;
                            }
                            PathOperationEntity pathOperationEntity = PathOperationEntity.builder().build();
                            List<String> tasList = opera.getTags();
                            String[] tags = new String[tasList.size()];
                            return pathOperationEntity.toBuilder()
                                    .tags(tasList.toArray(tags))
                                    .summary(opera.getSummary())
                                    .description(opera.getDescription())
                                    .roles(roles)
                                    .dependResources(resources)
                                    .build();
                        }
                )
                .orElse(null);
    }


    private OpenAPI createOpenApi(String apiFilePath) throws MojoExecutionException {
        try {
            return new OpenAPIV3Parser().read(this.apiFilePath);
        } catch (Exception e) {
            throw new MojoExecutionException("Read api file to string exception!");
        }
    }


    public static void main(String[] args) {
        ApiFileReader apiFileReader = new ApiFileReader("D:\\workspace\\idea\\swagger-codegen-maven-plugin/src/main/resources/test.yaml");
        Map map = apiFileReader.getPathApiDes();
    }
}
