/*
 * smart-doc https://github.com/shalousun/smart-doc
 *
 * Copyright (C) 2018-2020 smart-doc
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.power.doc.my;

import com.power.common.util.DateTimeUtil;
import com.power.doc.builder.DocBuilderTemplate;
import com.power.doc.builder.ProjectDocConfigBuilder;
import com.power.doc.model.ApiConfig;
import com.power.doc.model.ApiDoc;
import com.power.doc.model.SourceCodePath;
import com.thoughtworks.qdox.JavaProjectBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.power.doc.constants.DocGlobalConstants.*;

/**
 * @author zzg 20202-07-02
 */
public class ApiInterfaceDocBuilder {

    private static final String API_EXTENSION = "Api.md";

    private static final String DATE_FORMAT = "yyyyMMddHHmm";

    /**
     * @param config ApiConfig
     */
    public static void buildApiDoc(ApiConfig config) {
        JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();

        exPatternPath(config);
        buildApiDoc(config, javaProjectBuilder);
    }

    public static void exPatternPath(ApiConfig config) {
        if (config.getSourceCodePaths() != null && config.getSourceCodePaths().size() > 0) {

            List<SourceCodePath> sourceCodePaths = new ArrayList<>(config.getSourceCodePaths());
            List<SourceCodePath> newPaths = new ArrayList<>();

            for (SourceCodePath srcPath : sourceCodePaths) {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                try {
                    Resource[] resources = resolver.getResources("file:" + srcPath.getPath());
                    for (Resource res : resources) {
                        SourceCodePath s = new SourceCodePath();
                        s.setPath(res.getFile().getPath());
                        s.setDesc(res.getDescription());
                        newPaths.add(s);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            config.setSourceCodePaths(newPaths.toArray(new SourceCodePath[]{}));
        }
    }

    /**
     * Only for smart-doc maven plugin and gradle plugin.
     *
     * @param config             ApiConfig
     * @param javaProjectBuilder ProjectDocConfigBuilder
     */
    public static void buildApiDoc(ApiConfig config, JavaProjectBuilder javaProjectBuilder) {
        config.setAdoc(false);
        DocBuilderTemplate builderTemplate = new DocBuilderTemplate();
        builderTemplate.checkAndInit(config);
        ProjectDocConfigBuilder configBuilder = new ProjectDocConfigBuilder(config, javaProjectBuilder);
        InterfaceDocBuildTemplate docBuildTemplate = new InterfaceDocBuildTemplate();
        List<ApiDoc> apiDocList = docBuildTemplate.getApiData(configBuilder);
        if (config.isAllInOne()) {
            String version = config.isCoverOld() ? "" : "-V" + DateTimeUtil.long2Str(System.currentTimeMillis(), DATE_FORMAT);
            builderTemplate.buildAllInOne(apiDocList, config, javaProjectBuilder, ALL_IN_ONE_MD_TPL, "AllInOne" + version + ".md");
        } else {
            builderTemplate.buildApiDoc(apiDocList, config, API_DOC_MD_TPL, API_EXTENSION);
            builderTemplate.buildErrorCodeDoc(config, ERROR_CODE_LIST_MD_TPL, ERROR_CODE_LIST_MD);
            builderTemplate.buildDirectoryDataDoc(config, javaProjectBuilder, DICT_LIST_MD_TPL, DICT_LIST_MD);
        }
    }

    /**
     * Generate a single controller api document
     *
     * @param config         (ApiConfig
     * @param controllerName controller name
     */
    public static void buildSingleApiDoc(ApiConfig config, String controllerName) {
        config.setAdoc(false);
        JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
        ProjectDocConfigBuilder configBuilder = new ProjectDocConfigBuilder(config, javaProjectBuilder);
        DocBuilderTemplate builderTemplate = new DocBuilderTemplate();
        builderTemplate.checkAndInit(config);
        builderTemplate.buildSingleApi(configBuilder, controllerName, API_DOC_MD_TPL, API_EXTENSION);
    }
}
