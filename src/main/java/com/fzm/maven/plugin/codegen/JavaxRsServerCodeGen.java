package com.fzm.maven.plugin.codegen;

import com.fzm.maven.plugin.entity.CodeGenCfgEntity;
import org.openapitools.codegen.languages.JavaClientCodegen;


public class JavaxRsServerCodeGen extends JavaClientCodegen {

    private String servicePackage = "";

    private String feignPackage = "";

    private String feignFallBackPackage = "";

    private String serviceName ="";

    private String sdkoutputFolder ="";

    private String parentMvnArtifactId = "";

    private String projectVersion = "";
    public boolean needToImport(String type) {
        return super.needToImport(type) && type.indexOf(".") < 0;
    }

    public JavaxRsServerCodeGen(CodeGenCfgEntity codeGenCfg) {
        this.parentMvnArtifactId = codeGenCfg.getParentMvnArtifactId();
        this.projectVersion = codeGenCfg.getProjectVersion();
        this.sdkoutputFolder = codeGenCfg.getSdkoutputBasePath();
        this.outputFolder = codeGenCfg.getOutputBasePath();
        this.apiPackage = codeGenCfg.getApiPackage();
        this.modelPackage = codeGenCfg.getModelPackage();
        this.servicePackage = codeGenCfg.getServicePackage();
        this.feignPackage = codeGenCfg.getFeignPackage();
        this.feignFallBackPackage = codeGenCfg.getFeignFallBackPackage();
        this.serviceName = codeGenCfg.getServiceName();
    }

    public String servicePackage() {
        return servicePackage;
    }
    public String feignPackage() {
        return feignPackage;
    }
    public String feignFallBackPackage() {
        return feignFallBackPackage;
    }
    public String serviceName() {
        return serviceName;
    }
    public String sdkoutputFolder(){return sdkoutputFolder;}
    public String parentMvnArtifactId(){return parentMvnArtifactId;}
    public String projectVersion(){return projectVersion;}
}
