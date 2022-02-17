package com.fzm.maven.plugin.generator;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fzm.maven.plugin.codegen.JavaxRsServerCodeGen;
import com.fzm.maven.plugin.constant.DependecyHolder;
import com.fzm.maven.plugin.constant.PropertyKeys;
import com.fzm.maven.plugin.entity.*;
import com.fzm.maven.plugin.utils.StringTools;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.swagger.models.HttpMethod;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.maven.plugin.logging.Log;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.DefaultGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fzm.maven.plugin.constant.CodeGenConstant.*;

public class JavaxServerCodeGenerator extends DefaultGenerator {

    protected final Map<String, String> modelNameMap = new ConcurrentHashMap<>(16);
    private final String versionPattern = "v\\d+";

    protected final Map<String, PathEntity> pathEntityMap = new ConcurrentHashMap<>(32);

    protected final Set<ImportClassEntity> apiImportClassList = new HashSet<>(16);

    protected final Map<String, PathEntity> pathEntityMapAll = new ConcurrentHashMap<>(32);

    protected Set<ImportClassEntity> modelImportClassList = new HashSet<>();


    private final List<String> PATH_SUFFIX_ARRAY = new ArrayList(){{
        add("import");
        add("export");
        add("query");
        add("check");
        add("deploy");
    }};
    private final Map<String,String> HTTP_METHOD_NAME_MAPPING = new HashMap(){{
        put("POST","create");
        put("PUT","update");
        put("DELETE","delete");
        put("GET","get");
    }};

    @Getter
    private Log log;

    @Override
    public List<File> generate() {

        // 清理缓存
        this.clearCache();

        // 生成model类
        final Map<String, Schema> definitions = openAPI.getComponents().getSchemas();
        definitions.forEach(this::generateModel);

        // 存在不同path中有相同api类的场景, 先进行合并
        final Paths paths =openAPI.getPaths();
        paths.forEach(this::generatePathEntity);

        // 生成Delegate类
         this.pathEntityMap.forEach((key, value) -> this.writeToFile(value, key + "Delegate", DELEGATE_TEMPLATE_FILE));

        // 生成Controller类
        this.pathEntityMap.forEach((key, value) -> this.writeToFile(value, key + "Controller", CONTROLLER_TEMPLATE_FILE));

        //合并所有api 用于生成服务端feignsdk   后续需要修改到指定的包目录下 便于打包。
        JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        this.pathEntityMap.forEach((key, value) -> mergeAllPath(key.substring(0,key.indexOf('.')+1)+getNameForStrWithDash(codeGenCfg.serviceName()),value));
//
//
        this.pathEntityMapAll.forEach((key, value) -> this.writeToFile(value, key + "Api", FEIGN_TEMPLATE_FILE_SDK));
//
//
        this.pathEntityMapAll.forEach((key, value) -> this.writeToFile(value, key + key.substring(0,key.indexOf("."))+"FeignFallBackFactory", FEIGN_FALLBACK_TEMPLATE_FILE_SDK));

        writePom();

        return new ArrayList<>();
    }

    private void clearCache() {
        modelNameMap.clear();
        pathEntityMap.clear();
        pathEntityMapAll.clear();
        apiImportClassList.clear();
//        apiImportClassList.add(ImportClassEntity.builder()
//                .className("com.fzm.baas.base.model.BaasException")
//                .build());
    }

    protected void generateModel(String key, Schema model) {
        if(key.startsWith("IPage") || key.startsWith("PageResult") || key.startsWith("QuickQueryModel")){
            return;
        }
        key = key.replace("«","<").replace("»",">");
        JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        modelImportClassList = new HashSet<>();
        List<ModelPropEntity> propEntityList  = this.getModelProps(model);
        List<String> modelRefClassName = new ArrayList<>();
        propEntityList.stream().filter(modelPropEntity -> codeGenCfg.needToImport(modelPropEntity.getModelParamDesc().getType()))
                .forEach(modelPropEntity1 ->
                {
                    String typeName = modelPropEntity1.getModelParamDesc().getType();
                    if(StringUtils.isEmpty(typeName)){
                        return ;
                    }
                    if(!codeGenCfg.typeMapping().containsKey(typeName)
                        &&!codeGenCfg.typeMapping().containsValue(typeName)){
                        String refName = typeName;
                        if(refName.contains("<") && refName.contains(">")){
                            refName = refName.substring(refName.indexOf("<")+1,refName.length()-1);  //取得其中的泛型。
                        }
                        if(!codeGenCfg.typeMapping().containsKey(refName)
                                &&!codeGenCfg.typeMapping().containsValue(refName)) {
                            modelRefClassName.add(refName);
                        }
                    }
                    if(typeName.contains("<")){
                        typeName = typeName.split("<")[0];
                    }
                    String importClassName = codeGenCfg.importMapping().get(typeName);
                    if(StringUtils.isNotBlank(importClassName)){
                        modelImportClassList.add(
                                ImportClassEntity.builder()
                                        .className(toRemoveURICode(importClassName))
                                        .build());
                    }
                });
        final ModelClassEntity modelClassEntity = ModelClassEntity.builder()
                .modelPackage(codeGenCfg.modelPackage())
                .className(key)
                .modelProps(propEntityList)
                .importPackageList(modelImportClassList)
                .build();
        if(modelRefClassName.size() > 0){
            modelClassEntity.setRefModel(modelRefClassName);
        }
        // 缓存model全类名
        modelNameMap.put(key, codeGenCfg.modelPackage() + "." + key);

        processModel(modelClassEntity, key, MODEL_TEMPLATE_FILE);
    }

    private void generatePathEntity(String key, PathItem path) {
        String className = this.getClassNameFromPathString(key);
        if (Objects.isNull(className)) {
            return;
        }

        final JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        final String[] splitKeys = key.split("/\\{");
        if(splitKeys.length > 2){
            getLog().warn("warning : "+key+" contains more than one path variable ! would not be generated");
            return ;
        }
        String versionName = getVersionName(key);
        String fileName = versionName+"."+className;

        final PathEntity pathEntity = PathEntity.builder()
                .apiPackage(codeGenCfg.apiPackage()+"."+versionName)
                .servicePackage(codeGenCfg.servicePackage()+"."+versionName)
                .modelPackage(codeGenCfg.modelPackage())
                .feignPackage(codeGenCfg.feignPackage()+"."+versionName)
                .feignFallBackPackage(codeGenCfg.feignFallBackPackage()+"."+versionName)
                .versionName(versionName)
                .serviceName(codeGenCfg.serviceName())
                .className(className)
                .fileName(fileName)
                .urlPath(splitKeys[0])
                .importPackageList(apiImportClassList)
                .needImportModule("true")
                .build();
        if(path.getGet() != null){
            pathEntity.getPathGetEntityList().add(getPathOperationProps(path.getGet(), key,key,HttpMethod.GET.toString()));
        }
        if(path.getDelete() != null){
            pathEntity.getPathDeleteEntityList().add(getPathOperationProps(path.getDelete(), key,key,HttpMethod.DELETE.toString()));
        }
        if(path.getPost() != null){
            pathEntity.getPathPostEntityList().add(getPathOperationProps(path.getPost(), key,key,HttpMethod.POST.toString()));
        }
        if(path.getPut() != null){
            pathEntity.getPathPutEntityList().add(getPathOperationProps(path.getPut(), key,key,HttpMethod.PUT.toString()));
        }

        pathEntityMap.merge(fileName, pathEntity, (oldValue, newValue) -> {
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
    }

    //从完整路径中截取出版本号。如果版本号为空，则默认为V1
    protected String getVersionName(String fullPath){

        String versionName = "common";
        String[] pathArr = fullPath.split("/");
        if(Pattern.matches(versionPattern, pathArr[1])){
            versionName = pathArr[1];
        }
        return versionName;
    }



    private List<ModelPropEntity> getModelProps(Schema model) {
        JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        //获取不可为空字段
        List<String> requiredPropeties = model.getRequired();

        return ((Map<String, Schema>)model.getProperties()).entrySet()
                .stream()
                .map(entrySet -> {
                    ModelPropEntity modelPropEntity =  ModelPropEntity.builder()
                            .paramName(entrySet.getKey()).build();
                    String type = codeGenCfg.getTypeDeclaration(entrySet.getValue());
                    type = toRemoveURICode(type);
                    if("Boolean".equals(type)){
                        type = "boolean";
                    }
                    Object o = entrySet.getValue().getExample();
                    ModelParamDescEntity modelParamDescEntity =  ModelParamDescEntity.builder()
                            .required(requiredPropeties == null ? null : requiredPropeties.contains(entrySet.getKey()))
                            .type(type)
                            .description(entrySet.getValue().getDescription())
                            .example(o == null ? null : o.toString().replace("\"","\\\""))
                            .build();
                    return modelPropEntity.toBuilder().modelParamDesc(modelParamDescEntity).build();
                })
                .collect(Collectors.toList());
    }

    protected String getClassNameFromPathString(String path) {
        String[] pathArr = path.split("/");
        StringBuffer className = new StringBuffer();
        for(int i = 2 ; i<pathArr.length ; i++){
            String name = pathArr[i];
            if(name.contains("{") && name.contains("}")){
                continue;
            }
            if( (i == pathArr.length-1 && PATH_SUFFIX_ARRAY.contains(name))){
                break;
            }
            className.append(getNameForStrWithDash(name));
        }
        return className.toString();
    }

    protected String getNameForStrWithDash(String name){
        name = name.replace("{","").replace("}","");
        String[] nameArr = null;
        if(name.contains("-")){
            nameArr = name.split("-");
        }
        if(name.contains("_")){
            nameArr = name.split("_");
        }
        if(nameArr != null && nameArr.length > 0) {
            String pathName = "";
            for (String nameStr : nameArr) {
                pathName += toFirstUpperName(nameStr);
            }
            return pathName;
        }
        return toFirstUpperName(name);
    }



    protected PathOperationEntity getPathOperationProps(io.swagger.v3.oas.models.Operation operation, String urlPath, String fullPath, String restMethodName) {
        return Optional.ofNullable(operation)
                .map(opera ->
                        {
                            getDependencyPath(opera);
                            PathOperationEntity pathOperationEntity = PathOperationEntity.builder().build();
                            getRequestParams(opera.getParameters(),pathOperationEntity);
                            String methodName = getMethodNameByRestMethodName(urlPath,restMethodName);
                            String versionName =getVersionName(urlPath);
                            getRequestBodyParams(versionName,opera,pathOperationEntity,methodName);
                            return pathOperationEntity.toBuilder()
//                                    .methodName(removeUsingMethod(opera.getOperationId()))
                                    .methodName(methodName)
                                    .urlPath(urlPath)
                                    .fullPath(fullPath)
                                    .responseType(getResponseType(versionName,opera.getResponses(),methodName))
//                                    .params(params)
                                    .build();

                        }
                )
                .orElse(null);
    }


    protected void getRequestParams(List<io.swagger.v3.oas.models.parameters.Parameter> params, PathOperationEntity pathOperationEntity) {
        if (Objects.isNull(params) || params.isEmpty()) {
            return;
        }
        //过滤header 参数
        List<io.swagger.v3.oas.models.parameters.Parameter> notHeaderParams = params.stream().filter(parameter -> !parameter.getIn().equals("header")).collect(Collectors.toList());
        // 存在多个入参的场景, mustache处理逻辑有限, 只能特殊处理
        String paramName;
        Map<String, String> paramNameMap = new HashMap<>(notHeaderParams.size());
        for (int i = 0; i < notHeaderParams.size(); i++) {
            paramName = notHeaderParams.get(i).getName();
            String paramNameStr = handlerParamName(paramName);
            if (i != notHeaderParams.size() - 1) {
                paramNameMap.put(paramName, paramNameStr + ",");
            } else {
                paramNameMap.put(paramName, paramNameStr);
            }
        }
        pathOperationEntity.getParams().addAll(notHeaderParams.stream().map(param -> {
                    String paramType = getParamType(param);
                    ParamsEntity paramsEntity = ParamsEntity.builder()
                            .paramName(paramNameMap.get(param.getName()))
                            .paramType(paramType)
                            .build();
                    if("body".equals(param.getIn())){
                        ApiParamDescription apiParamDescription =  ApiParamDescription.builder().flag("true").build();
                        if("MultipartFile".equals(paramType)){
                            pathOperationEntity.setUploadRequest("true");    //增加标志，后续生成模板中据此判断是否是上传请求
                        }
                        paramsEntity.setBodyParamTag(apiParamDescription);
                    }else if("path".equals(param.getIn())){
                        paramsEntity.setPathParamTag(ApiParamDescription.builder().required(param.getRequired()).flag(param.getName()).build());
                    }else if("query".equals(param.getIn())){
                        paramsEntity.setQueryParamTag(ApiParamDescription.builder().required(param.getRequired()).flag(param.getName()).build());//
                    }
                    return paramsEntity;
                }
        ).collect(Collectors.toList()));
    }


    protected void getRequestBodyParams(String versionName,io.swagger.v3.oas.models.Operation operation, PathOperationEntity pathOperationEntity,String methodName) {
        if (Objects.isNull(operation.getRequestBody())) {
            return;
        }
        MediaType mt = operation.getRequestBody().getContent().get("multipart/form-data");
        boolean isFormData = true;
        if(mt == null ){
            mt = operation.getRequestBody().getContent().get("application/json");
            isFormData = false;
        }
        if(mt == null){
            return ;
        }
        List<String> requiredList = mt.getSchema().getRequired();
        Schema paramSchema = mt.getSchema();
        String paramName;
        if( !isFormData
            && "Object".equals(((JavaxRsServerCodeGen) config).getSchemaType(mt.getSchema()))
            && mt.getSchema().getProperties()!= null
                && mt.getSchema().getProperties().size() > 0){
            String paramType = toFirstUpperName(methodName)+"RequestBody"+versionName;
            generateModel(paramType,mt.getSchema());
            ParamsEntity paramsEntity = ParamsEntity.builder()
                    .paramName(toFirstLowerName(paramType))
                    .paramType(paramType)
                    .build();
            pathOperationEntity.setParams(new ArrayList(){{ add(paramsEntity);}});

            //加入feignClinet依赖
            apiImportClassList.add(ImportClassEntity.builder()
                    .className(((JavaxRsServerCodeGen) config).modelPackage()+"."+toRemoveURICode(paramType))
                    .build());
            return;
        }
        if(null != ((JavaxRsServerCodeGen) config).getSchemaType(mt.getSchema()) && !isFormData){
            String paramType = config.getTypeDeclaration(mt.getSchema());
            ApiParamDescription apiParamDescription =  ApiParamDescription.builder().flag("true").build();
            ParamsEntity paramsEntity = ParamsEntity.builder()
                    .paramName(toFirstLowerName(paramType))
                    .paramType(paramType)
                    .bodyParamTag(apiParamDescription)
                    .build();
            pathOperationEntity.setParams(new ArrayList(){{ add(paramsEntity);}});
            String className = paramType.contains("<")? paramType.substring(paramType.indexOf("<")+1,paramType.length()-1) : paramType;
            apiImportClassList.add(ImportClassEntity.builder()
                    .className(modelNameMap.get(toRemoveURICode(className)))
                    .build());
            return;
        }
        if(paramSchema.getProperties() == null || paramSchema.getProperties().size() == 0){
            return;
        }
        Map<String,Schema> paramMap =  paramSchema.getProperties();
        // 存在多个入参的场景, mustache处理逻辑有限, 只能特殊处理
        Map<String, String> paramNameMap = new HashMap<>(paramMap.size());
        int i = 0;
        boolean hasRequestParam = pathOperationEntity.getParams().size() > 0 ;
        for (Map.Entry<String, Schema> entry : paramMap.entrySet()) {
            paramName = entry.getKey();
            String paramNameStr = handlerParamName(paramName);
            if (i != paramMap.size() - 1) {
                paramNameStr =  paramNameStr + ",";
            }
            paramNameMap.put(paramName, paramNameStr);
            i++;
        }
        final boolean[] ifFirstParam = new boolean[]{true};    //特殊处理第一个参数。前面要加 “，”
        pathOperationEntity.getParams().addAll(paramMap.entrySet().stream().map(param -> {
                    String paramType = ((JavaxRsServerCodeGen) config).getSchemaType(param.getValue());
                    paramType = "File".equals(paramType) ? "MultipartFile" : paramType;
                    ParamsEntity paramsEntity = ParamsEntity.builder()
                    .paramName(paramNameMap.get(param.getKey()))
                    .build();
                    if(ifFirstParam[0] && hasRequestParam){
                        paramsEntity = paramsEntity.toBuilder().hasRequestParams("true").build();
                    }
                    paramsEntity = paramsEntity.toBuilder().paramType(paramType).build();
                    if("binary".equals(param.getValue().getFormat())){
                        ApiParamDescription apiParamDescription =  ApiParamDescription.builder().flag("true").build();
                        if("MultipartFile".equals(paramType)){
                            pathOperationEntity.setUploadRequest("true");    //增加标志，后续生成模板中据此判断是否是上传请求
                            apiParamDescription.setFileUpLoadTag("true");
                        }
                        paramsEntity.setBodyParamTag(apiParamDescription);
                    }else{
                        paramsEntity.setQueryParamTag(ApiParamDescription.builder().flag(param.getKey()).required(requiredList == null ? false : requiredList.contains(param.getKey())).build());
                    }
                    ifFirstParam[0] = false;
                    return paramsEntity;
                }
        ).collect(Collectors.toList()));
    }


    private String handlerParamName(String paramName){
        String[] paramNameStrArr = null;
        if(paramName.indexOf("_") > 0){
            paramNameStrArr = paramName.split("_");
        }else if(paramName.indexOf("-") > 0){
            paramNameStrArr = paramName.split("-");
        }
        if( null != paramNameStrArr){
            return paramNameStrArr[0]+toFirstUpperName(paramNameStrArr[1]);
        }else{
            return paramName;
        }
    }


    protected void getDependencyPath(io.swagger.v3.oas.models.Operation operation){
        Optional.ofNullable(operation.getExtensions())
                .map(oper ->
                        {
                            if (oper.containsKey(PropertyKeys.DEPENDENCY_API_DES_KEY)) {
                                String dependApisStr = String.valueOf(oper.get(PropertyKeys.DEPENDENCY_API_DES_KEY));
                                JSONObject dependencyObject = JSONObject.parseObject(dependApisStr);
                                dependencyObject.entrySet().stream().forEach(stringObjectEntry ->
                                        {
                                            Map<String,List<String>> singleServiceDependencies = new HashMap<String,List<String>>();
                                            JSONArray dependApiArr = (JSONArray) stringObjectEntry.getValue();
                                            for(int i = 0 ; i< dependApiArr.size();i++){
                                                String[] depApiPathAndMethod = dependApiArr.get(i).toString().split(" ");
                                                if(singleServiceDependencies.containsKey(depApiPathAndMethod[1])){
                                                    singleServiceDependencies.get(depApiPathAndMethod[1]).add(depApiPathAndMethod[0]);
                                                }else{
                                                    singleServiceDependencies.put(depApiPathAndMethod[1],new ArrayList(){{
                                                        add(depApiPathAndMethod[0]);
                                                    }});
                                                }
                                            }
                                            DependecyHolder.currDepencyWithService.merge(StringTools.getSubstringBetweenFF(stringObjectEntry.getKey(),"$","$"),singleServiceDependencies, (oldValue, newValue) ->
                                            {
                                                newValue.entrySet().stream().forEach(newValueEnt -> {
                                                    if(oldValue.containsKey(newValueEnt.getKey())){
                                                        oldValue.get(newValueEnt.getKey()).addAll(newValueEnt.getValue());
                                                    }else{
                                                        oldValue.put(newValueEnt.getKey(),newValueEnt.getValue());
                                                    }
                                                });
                                                return oldValue;
                                            });

                                        }
                                );
                            }
                            return null;
                        }
                )
                .orElse(null);
    }


    private String getMethodNameByRestMethodName(String url,String restMethodName){
        String[] pathStrArr = url.split("/");
        String endStr = pathStrArr[pathStrArr.length-1];
        if(PATH_SUFFIX_ARRAY.contains(endStr)){
            restMethodName = endStr;
        }else{
            restMethodName = HTTP_METHOD_NAME_MAPPING.get(restMethodName);
        }
        String resourceName = getClassNameFromPathString(url);
        String condition = "";
        if(endStr.startsWith("{") && endStr.endsWith("}")){
            condition = "By"+getNameForStrWithDash(endStr);
        }
        return (restMethodName.toLowerCase()+toFirstUpperName(resourceName))+condition;
    }



    private String toFirstUpperName(String str){
        String first = str.substring(0, 1);
        String after = str.substring(1);
        return first.toUpperCase()+ after;
    }


    private String toFirstLowerName(String str){
        if(str.contains("<")){
            String[] generic = str.replace(">","").split("<");
            String first = generic[1].substring(0, 1);
            String after = generic[1].substring(1);
            return first.toLowerCase()+ after+generic[0];
        }
        String first = str.substring(0, 1);
        String after = str.substring(1);
        return first.toLowerCase()+ after;
    }


    private String getResponseType(String versionName,Map<String, ApiResponse> responseMap,String methodName) {
        Optional<Map.Entry<String, ApiResponse>> entryOptional = responseMap.entrySet()
                .stream()
                .filter(entrySet -> entrySet.getKey().equals(String.valueOf(HttpStatus.SC_OK)))
                .findAny();
        Optional<Content> contentOptional = entryOptional.map(Map.Entry::getValue)
                .map(ApiResponse::getContent);
        if(contentOptional.isPresent()) {
            final Schema scheme = contentOptional.get().entrySet().stream().findAny().get().getValue().getSchema();
            String className ;
            //兼容返回Object 组合消息体
            if("Object".equals(((JavaxRsServerCodeGen) config).getSchemaType(scheme))
                    && scheme.getProperties()!= null
                    && scheme.getProperties().size() > 0){
                className = toFirstUpperName(methodName)+"ResponseBody"+versionName;
                generateModel(className,scheme);
                return className;
            }
            if (null != scheme.get$ref()) {
                className = ((JavaxRsServerCodeGen) config).getSchemaType(scheme);
                //暂时不加入引用，在模板中写死import。后续改成引用列表。
                if(!className.startsWith("IPage") && !className.startsWith("Page") && !className.startsWith("QuickQueryModel")){
                    apiImportClassList.add(ImportClassEntity.builder()
                            .className(modelNameMap.get(toRemoveURICode(className)))
                            .build());
                }else{
                    className = new DefaultCodegen().getSchemaType(scheme);  //取爷爷类的函数获取类型，避免丢失泛型符号。
                    JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
                    className = toRemoveURICode(className.replace("«","<").replace("»",">"));
                    final String ipageGeneric = className.substring(className.indexOf("<")+1,className.length()-1);
                    if(apiImportClassList.stream().filter(clazzName -> ipageGeneric.equals(clazzName.getClassName())).count() == 0){
                        apiImportClassList.add(ImportClassEntity.builder()
                                .className(codeGenCfg.modelPackage()+"."+ipageGeneric)
                                .build());
                    }
                }
            } else {
                JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
                className = codeGenCfg.getTypeDeclaration(scheme);
                if("Object".equals(className)){
                    if(scheme.getProperties() == null || scheme.getProperties().size() == 0){
                        return null;
                    }
                }
                if(scheme instanceof ArraySchema){
                    if(codeGenCfg.needToImport(className)) {
                        apiImportClassList.add(ImportClassEntity.builder()
                                .className(codeGenCfg.importMapping().get("List"))
                                .build());
                    }
                    className = toRemoveURICode(className);
                    String arrayGeneric = className.substring(className.indexOf("<")+1,className.length()-1);
                    if(apiImportClassList.stream().filter(clazzName -> arrayGeneric.equals(clazzName.getClassName())).count() == 0){
                        apiImportClassList.add(ImportClassEntity.builder()
                                .className(codeGenCfg.modelPackage()+"."+arrayGeneric)
                                .build());
                    }
                }
            }
            return toRemoveURICode(className);
        }
        return null;

    }

    private String toRemoveURICode(String var){
        if(StringUtils.isEmpty(var)){
            return null;
        }
        return  var.replace("«","<")
                .replace("»",">")
                .replace("%3C","<")
                .replace("%3E",">")
                .replace("%C2%AB","<")
                .replace("%C2%BB",">");
    }


    private String getParamType(Parameter parameter) {
        final String parameterIn = parameter.getIn();
        if (parameterIn.equals("body")) {
            final Schema schema = parameter.getSchema();
            String refStr = schema.get$ref();
            String className = refStr.substring(refStr.lastIndexOf("/") + 1, refStr.length());
            apiImportClassList.add(ImportClassEntity.builder()
                    .className(modelNameMap.get(toRemoveURICode(className)))
                    .build());
            return className;
        } else {
            final String paramType = parameter.getSchema().getType();
            return config.typeMapping().get(paramType);
        }
    }

    protected String getOutputFilePath(String fileName, String templateName) {
        JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
        if(fileName.contains(".")){
            fileName = fileName.replace(".",File.separator);
        }
        String packageName = codeGenCfg.apiPackage();
        if (DELEGATE_TEMPLATE_FILE.equals(templateName)) {
            packageName = codeGenCfg.servicePackage();
        } else if (MODEL_TEMPLATE_FILE.equals(templateName) || MODEL_TEMPLATE_FILE_SDK.equals(templateName)) {
            packageName = codeGenCfg.modelPackage();
        } else if (FEIGN_TEMPLATE_FILE.equals(templateName) || FEIGN_TEMPLATE_FILE_SDK.equals(templateName)) {
            packageName = codeGenCfg.feignPackage();
        } else if (FEIGN_FALLBACK_TEMPLATE_FILE.equals(templateName) || FEIGN_FALLBACK_TEMPLATE_FILE_SDK.equals(templateName)) {
            packageName =  codeGenCfg.feignFallBackPackage();
        }

        if( MODEL_TEMPLATE_FILE_SDK.equals(templateName)
                || FEIGN_FALLBACK_TEMPLATE_FILE_SDK.equals(templateName)
                ||FEIGN_TEMPLATE_FILE_SDK.equals(templateName)){
            return codeGenCfg.sdkoutputFolder() + File.separator
                    + packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator))
                    + File.separator + fileName + ".java";
        }
        return codeGenCfg.outputFolder() + File.separator
                + packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator))
                + File.separator + fileName + ".java";
    }

    private String getTempFileString(String fileName) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
        if (Objects.isNull(resourceAsStream)) {
            return EMPTY_STRING;
        }

        return new BufferedReader(new InputStreamReader(resourceAsStream))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    protected void processModel(Object context, String fileName, String templateName) {
        writeToFile( context,  fileName,  templateName);
        writeToFile( context,  fileName,  MODEL_TEMPLATE_FILE_SDK);
    }

    protected void writeToFile(Object context, String fileName, String templateName) {
        try {

            String outputPath = this.getOutputFilePath(fileName, templateName);

            templateName = templateName.replace(".sdk","");
            // 获取模板信息
            String templateFileString = this.getTempFileString(templateName);

            // 生成模板
            Template template = Mustache.compiler().compile(templateFileString);

            // 解析模板
            String executeResult = template.execute(context);

            // 生成java类


            FileUtils.writeStringToFile(new File(outputPath), executeResult, StandardCharsets.UTF_8, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected void writePom() {
        try {
            JavaxRsServerCodeGen codeGenCfg = (JavaxRsServerCodeGen) config;
            PomEntity pomEntity = PomEntity.builder().parentMvnArtifactId(((JavaxRsServerCodeGen) config).parentMvnArtifactId()).projectVersion(((JavaxRsServerCodeGen) config).projectVersion()).build();
            String outputPath = codeGenCfg.sdkoutputFolder();
            String pomPath = outputPath.substring(0,outputPath.indexOf("target")) + "pom.xml";
            // 获取模板信息
            String templateFileString = this.getTempFileString("pom.mustache");
            // 生成模板
            Template template = Mustache.compiler().compile(templateFileString);
            // 解析模板
            String executeResult = template.execute(pomEntity);
            FileUtils.writeStringToFile(new File(pomPath), executeResult, StandardCharsets.UTF_8, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public JavaxServerCodeGenerator log(Log log) {
        this.log = log;
        return this;
    }




    private void mergeAllPath(String fileName,PathEntity pathEntityIn){
        pathEntityMapAll.merge(fileName, pathEntityIn, (oldValue, newValue) -> {
            // 合并importList
            Set<ImportClassEntity> oldImportSet = oldValue.getImportPackageList();
            newValue.getImportPackageList().addAll(oldImportSet);
            newValue.setClassName(fileName.substring(fileName.indexOf(".")+1));
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
}