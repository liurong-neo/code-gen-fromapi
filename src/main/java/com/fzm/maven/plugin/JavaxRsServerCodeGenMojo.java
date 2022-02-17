package com.fzm.maven.plugin;

import com.fzm.maven.plugin.constant.CodeGenConstant;
import com.fzm.maven.plugin.constant.DependecyHolder;
import com.fzm.maven.plugin.entity.CodeGenCfgEntity;
import com.fzm.maven.plugin.entity.ServiceEntity;
import com.fzm.maven.plugin.generator.JavaxClientCodeGenerator;
import com.fzm.maven.plugin.generator.JavaxServerCodeGenerator;
import com.fzm.maven.plugin.codegen.JavaxRsServerCodeGen;
import com.fzm.maven.plugin.utils.GitLabAPIUtil;
import com.fzm.maven.plugin.utils.YamlTools;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.openapitools.codegen.ClientOptInput;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.fzm.maven.plugin.constant.CodeGenConstant.*;

@Mojo(name = "codegen", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JavaxRsServerCodeGenMojo extends AbstractMojo
{
    @Parameter(readonly=true, required=true, defaultValue="${project}")
    private MavenProject mvnProject; // 当前maven工程, 只读


    private final String SERVICES_GIT_PATH = "baas3/services/%s";


    private final String API_DOC_URI = "/service-impl/src/main/resources/api.yaml";

    private Map<String, ServiceEntity> dependencyServiceMap = new HashMap<>();

    private final String DEPENDENCE_SERVICES_NAME_KEY = "dependency.services";

    private final String DEPENDENCE_SERVICE_ALIAS_NAME_KEY =  "alias-name";

    private final String DEPENDENCE_SERVICE_REAL_NAME_KEY =  "real-name";

    private final String DEPENDENCE_SERVICE_VERSION_KEY =  "version";
    private String gitIP= "gitlab.33.cn";
    private String inputSpec;
    private String moduleSpec;
    private String gitToken;
    private String serverPack;
    public void execute() throws MojoExecutionException {
        getLog().info("Start to generator code !");
        this.serverPack = Optional.ofNullable(this.serverPack).orElse(mvnProject.getArtifactId().split("-")[0]);
        this.gitToken = Optional.ofNullable(this.gitToken).orElse("vciJ2Z1RAopgVAZ1dGgg");
        this.gitIP = Optional.ofNullable(this.gitIP).orElse("gitlab.33.cn");
        // 未指定api文件, 使用默认值
        try {
            this.inputSpec = Optional.ofNullable(this.inputSpec).orElse(this.mvnProject.getBasedir().getCanonicalPath()+File.separator+CodeGenConstant.API_FILE_PATH);
        } catch (IOException e) {
            throw new MojoExecutionException("api file path can not be empty !");
        }
        try {
            this.moduleSpec = Optional.ofNullable(this.moduleSpec).orElse(this.mvnProject.getBasedir().getCanonicalPath()+File.separator+CodeGenConstant.MODULE_FILE_PATH);
        } catch (IOException e) {
            throw new MojoExecutionException("module file path can not be empty !");
        }
        // 清理前一次编译生成的类文件
        this.clearOldFile();
        // 创建swagger对象
        OpenAPI openAPI = this.createOpenApi();
        //创建外部swagger对象。（此处封装全量）
        Map<String,OpenAPI> otherOpenApis = new HashMap<>();
        // 生成Java类
        this.generateJavaClass(openAPI);

        //初始化依赖的服务的 别名、实名、版本等。
        initDependencyServiceInfo();
        DependecyHolder.currDepencyWithService.entrySet().stream().forEach(
                depencywithService -> {
                    String dependencyServiceAliasName = depencywithService.getKey();
                    String dependencyServiceRealName = dependencyServiceAliasName;   //不配置用原名
                    String branchName ="master";
                    if(dependencyServiceMap.containsKey(dependencyServiceAliasName)){
                        ServiceEntity serviceInfo = dependencyServiceMap.get(dependencyServiceAliasName);
                        if(null != serviceInfo) {
                            if (StringUtils.isNotBlank(serviceInfo.getRealName())) {
                                dependencyServiceRealName = serviceInfo.getRealName();
                            }
                            if(StringUtils.isNotBlank(serviceInfo.getVersion())){
                                branchName =serviceInfo.getVersion();
                            }
                        }
                    }
                    try {
                        String apiContent = GitLabAPIUtil.getFileContentFromRepository(this.gitIP,String.format(SERVICES_GIT_PATH,dependencyServiceRealName), API_DOC_URI,branchName,gitToken);
                        otherOpenApis.put(depencywithService.getKey(), new OpenAPIV3Parser().readContents(apiContent).getOpenAPI());
                    } catch (Exception e) {
                        getLog().warn("get file from git fail , file is :" +String.format(SERVICES_GIT_PATH,dependencyServiceRealName)+" branchName:"+branchName+" error:"+e.getMessage());
                    }
                }
        );

        if(DependecyHolder.currDepencyWithService.size() > 0){
            this.generateFeignClass(otherOpenApis);
        }

    }

    private void clearOldFile() throws MojoExecutionException {
        try {
            String canonicalPath = this.mvnProject.getBasedir().getCanonicalPath();
            String oldFilePath = canonicalPath + TARGET_PATH;
            File file = new File(oldFilePath);
            if (file.exists()) {
                FileUtils.cleanDirectory(file);
            }
            String parentCanonicalPath = this.mvnProject.getParent().getBasedir().getCanonicalPath();
            String oldSdkFilePath = parentCanonicalPath+ File.separator + "sdk";
            File file1 = new File(oldSdkFilePath);
            if (file1.exists()) {
                FileUtils.cleanDirectory(file1);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Clear old generate file exception!");
        }
    }

    private OpenAPI createOpenApi() throws MojoExecutionException {
        try {
            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            parser.setEncoding("UTF-8");
            return parser.read(this.inputSpec);
        } catch (Exception e) {
            throw new MojoExecutionException("Read api file to string exception!");
        }
    }

    private void generateJavaClass(OpenAPI openAPI) throws MojoExecutionException {
        String outputBaseFilePath;
        String sdkoutputBaseFilePath;
        try {
            String canonicalPath = this.mvnProject.getBasedir().getCanonicalPath();
            outputBaseFilePath = canonicalPath + TARGET_PATH;
            String parentcanonicalPath = this.mvnProject.getParent().getBasedir().getCanonicalPath();
            sdkoutputBaseFilePath = parentcanonicalPath + SDK_TARGET_PATH;
        } catch (IOException e) {
            throw new MojoExecutionException("Generate model file exception!");
        }
        String parentArtifactId = mvnProject.getParent().getArtifactId();
        final CodeGenCfgEntity codeGenConfig = CodeGenCfgEntity.builder()
                .apiPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"controller"))
                .apiTemplate(CodeGenConstant.CONTROLLER_TEMPLATE_FILE)
                .modelPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"model"))
                .modelTemplate(CodeGenConstant.MODEL_TEMPLATE_FILE)
                .servicePackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"delegate"))
                .serviceTemplate(CodeGenConstant.DELEGATE_TEMPLATE_FILE)
                .feignPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"api"))
                .feignTemplate(FEIGN_TEMPLATE_FILE_SDK)
                .feignFallBackPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"api"))
                .feignFallBackTemplate(FEIGN_FALLBACK_TEMPLATE_FILE_SDK)
                .serviceName(parentArtifactId)

                .parentMvnArtifactId(parentArtifactId)
                .projectVersion(mvnProject.getVersion())

                .outputBasePath(outputBaseFilePath)
                .sdkoutputBasePath(sdkoutputBaseFilePath)
                .build();

        JavaxRsServerCodeGen javaxRsServerCodeGen = new JavaxRsServerCodeGen(codeGenConfig);
        ClientOptInput clientOptInput = new ClientOptInput().openAPI(openAPI);
        clientOptInput.config(javaxRsServerCodeGen);
                JavaxServerCodeGenerator javaCodeGenerator = new JavaxServerCodeGenerator();
        javaCodeGenerator.log(getLog()).opts(clientOptInput).generate();
    }

    private void generateFeignClass(Map<String,OpenAPI> openAPIMap) throws MojoExecutionException {
        String outputBaseFilePath;

       try {
            String canonicalPath = this.mvnProject.getBasedir().getCanonicalPath();
            outputBaseFilePath = canonicalPath + CodeGenConstant.TARGET_PATH;
        } catch (IOException e) {
            throw new MojoExecutionException("Generate model file exception!");
        }


        final CodeGenCfgEntity codeGenConfig = CodeGenCfgEntity.builder()
                .modelPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"model"))
                .modelTemplate(CodeGenConstant.MODEL_TEMPLATE_FILE)
                .feignPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"api"))
                .feignTemplate(CodeGenConstant.FEIGN_TEMPLATE_FILE)
                .feignFallBackPackage(String.format(CodeGenConstant.PACKAGE_PATH,serverPack,"api"))
                .feignFallBackTemplate(CodeGenConstant.FEIGN_FALLBACK_TEMPLATE_FILE)
                .outputBasePath(outputBaseFilePath)
                .build();

        JavaxRsServerCodeGen javaxRsServerCodeGen = new JavaxRsServerCodeGen(codeGenConfig);
        ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.config(javaxRsServerCodeGen);

        JavaxClientCodeGenerator javaCodeGenerator = new JavaxClientCodeGenerator();
        javaCodeGenerator.opts(clientOptInput);
        javaCodeGenerator.otherOpenApi(openAPIMap);
        javaCodeGenerator.dependenciesPathsWithServiceName(DependecyHolder.currDepencyWithService);
        javaCodeGenerator.dependencyServiceMap(dependencyServiceMap);
        javaCodeGenerator.generateForFeign();
    }




    private void initDependencyServiceInfo(){
        if(!new File(moduleSpec).exists())
        {
            getLog().warn(moduleSpec + " does not exists");
            return ;  //文件不存在，退出
        }
        YamlTools yamlTools = new YamlTools(moduleSpec);
        List<Map<String,String>> serviceModules = yamlTools.getValueByKey(DEPENDENCE_SERVICES_NAME_KEY,new ArrayList());
        serviceModules.stream().forEach(map ->

                dependencyServiceMap.put(map.get(DEPENDENCE_SERVICE_ALIAS_NAME_KEY),ServiceEntity.builder()
                        .aliasName(map.get(DEPENDENCE_SERVICE_ALIAS_NAME_KEY))
                        .realName(map.get(DEPENDENCE_SERVICE_REAL_NAME_KEY))
                        .version(map.get(DEPENDENCE_SERVICE_VERSION_KEY))
                        .build())
        );
    }

    public static void main(String[] args) {
        JavaxRsServerCodeGenMojo mojo = new JavaxRsServerCodeGenMojo();
        try {
            mojo.execute();
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}