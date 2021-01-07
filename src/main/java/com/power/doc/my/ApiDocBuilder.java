package com.power.doc.my;

import com.alibaba.fastjson.JSON;
import com.power.doc.model.ApiConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ApiDocBuilder {
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");


    /**
     * mvn -DskipTests=true  -Dmaven.yuicompressor.skip=true  -Dmaven.exec.skip=false -Dmaven.exec.arg1=${genLang} clean package -pl ":hessian-objc" -am
     */
    public static void main(String[] args) {
        String rootPath = args[0];
        String configFile = args[1];
        String outputPath = args[2];

        try {
            String json = new String(Files.readAllBytes(Paths.get(configFile)), StandardCharsets.UTF_8);
            ApiConfig apiConfig = JSON.parseObject(json, ApiConfig.class);
            apiConfig.setOutPath(outputPath);
            apiConfig.getSourceCodePaths().forEach(sourceCodePath -> sourceCodePath.setPath(rootPath + FILE_SEPARATOR + sourceCodePath.getPath()));

            HtmlApiDocBuilder.buildApiDoc(apiConfig);
            HtmlOpenApiDocBuilder.buildApiDoc(apiConfig);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
