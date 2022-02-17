package com.fzm.maven.plugin.constant;

import java.io.File;

public final class CodeGenConstant {

    private CodeGenConstant() {
    }

    public static final String EMPTY_STRING = "";

//    public static final String JAVA_SRC_PATH = File.separator + "src" + File.separator + "main" + File.separator + "java";
    public static final String TARGET_PATH = File.separator + "target" + File.separator + "generated-sources" + File.separator;

    public static final String SDK_TARGET_PATH = File.separator + "sdk" + File.separator + "target" + File.separator + "generated-sources" + File.separator;

    public static final String PACKAGE_PATH = "com.fzm.baas.%s.%s";

    public static final String MODEL_TEMPLATE_FILE = "model.mustache";

    public static final String DELEGATE_TEMPLATE_FILE = "delegate.mustache";

    public static final String CONTROLLER_TEMPLATE_FILE = "controller.mustache";

    public static final String FEIGN_TEMPLATE_FILE = "feign.mustache";

    public static final String FEIGN_FALLBACK_TEMPLATE_FILE = "feignfallback.mustache";

    public static final String FEIGN_FALLBACK_TEMPLATE_FILE_SDK = "feignfallback.mustache.sdk";

    public static final String FEIGN_TEMPLATE_FILE_SDK = "feign.mustache.sdk";

    public static final String MODEL_TEMPLATE_FILE_SDK = "model.mustache.sdk";

    public static final String APIFOX_FILE_PATH = "src/main/resources/apifox.json";
    public static final String API_FILE_PATH = "src/main/resources/api.yaml";
    public static final String MODULE_FILE_PATH = "src/main/resources/module.yaml";

}