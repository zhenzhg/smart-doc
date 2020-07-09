/*
 * smart-doc
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

import com.alibaba.fastjson.JSON;
import com.power.common.util.JsonFormatUtil;
import com.power.common.util.StringUtil;
import com.power.common.util.UrlUtil;
import com.power.doc.builder.ProjectDocConfigBuilder;
import com.power.doc.constants.DocAnnotationConstants;
import com.power.doc.constants.DocGlobalConstants;
import com.power.doc.constants.DocTags;
import com.power.doc.constants.SpringMvcAnnotations;
import com.power.doc.helper.JsonBuildHelper;
import com.power.doc.helper.ParamsBuildHelper;
import com.power.doc.model.*;
import com.power.doc.model.request.ApiRequestExample;
import com.power.doc.model.request.RequestMapping;
import com.power.doc.template.IDocBuildTemplate;
import com.power.doc.utils.DocClassUtil;
import com.power.doc.utils.DocUtil;
import com.power.doc.utils.JavaClassUtil;
import com.power.doc.utils.JavaClassValidateUtil;
import com.thoughtworks.qdox.model.*;
import com.thoughtworks.qdox.model.expression.AnnotationValue;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.power.doc.constants.DocGlobalConstants.JSON_CONTENT_TYPE;
import static com.power.doc.constants.DocTags.IGNORE;
import static com.power.doc.utils.DocClassUtil.processTypeNameForParams;

/**
 * @author zzg 20202-07-02
 */
public class InterfaceDocBuildTemplate implements IDocBuildTemplate<ApiDoc> {

    private List<ApiReqHeader> headers;

    @Override
    public List<ApiDoc> getApiData(ProjectDocConfigBuilder projectBuilder) {

        ApiConfig apiConfig = projectBuilder.getApiConfig();
        this.headers = apiConfig.getRequestHeaders();
        List<ApiDoc> apiDocList = new ArrayList<>();
        int order = 0;
        Collection<JavaClass> classes = projectBuilder.getJavaProjectBuilder().getClasses();
        for (JavaClass cls : classes) {
            String ignoreTag = JavaClassUtil.getClassTagsValue(cls, DocTags.IGNORE, Boolean.FALSE);
//            if (!checkController(cls) || StringUtil.isNotEmpty(ignoreTag)) {
//                continue;
//            }
            if (!cls.getFullyQualifiedName().matches("^com\\.x\\.mymall\\..*\\.contract.service\\..*$") || !cls.isInterface() || StringUtil.isNotEmpty(ignoreTag)) {
                continue;
            }
            if (StringUtil.isNotEmpty(apiConfig.getPackageFilters())) {
                if (DocUtil.isMatch(apiConfig.getPackageFilters(), cls.getCanonicalName())) {
                    order++;
                    List<ApiMethodDoc> apiMethodDocs = buildControllerMethod(cls, apiConfig, projectBuilder);
                    this.handleApiDoc(cls, apiDocList, apiMethodDocs, order, apiConfig.isMd5EncryptedHtmlName());
                }
            } else {
                order++;
                List<ApiMethodDoc> apiMethodDocs = buildControllerMethod(cls, apiConfig, projectBuilder);
                this.handleApiDoc(cls, apiDocList, apiMethodDocs, order, apiConfig.isMd5EncryptedHtmlName());
            }
        }
        // sort
        if (apiConfig.isSortByTitle()) {
            Collections.sort(apiDocList);
        }
        return apiDocList;
    }

    @Override
    public ApiDoc getSingleApiData(ProjectDocConfigBuilder projectBuilder, String apiClassName) {
        return null;
    }

    @Override
    public boolean ignoreReturnObject(String typeName) {
        if (JavaClassValidateUtil.isMvcIgnoreParams(typeName)) {
            return DocGlobalConstants.MODE_AND_VIEW_FULLY.equals(typeName);
        }
        return false;
    }

    private List<ApiMethodDoc> buildControllerMethod(final JavaClass cls, ApiConfig apiConfig, ProjectDocConfigBuilder projectBuilder) {
        String clazName = cls.getCanonicalName();
        String classAuthor = JavaClassUtil.getClassTagsValue(cls, DocTags.AUTHOR, Boolean.FALSE);
        List<JavaAnnotation> classAnnotations = cls.getAnnotations();
        String baseUrl = "";
        for (JavaAnnotation annotation : classAnnotations) {
            String annotationName = annotation.getType().getValue();
            if (DocAnnotationConstants.REQUEST_MAPPING.equals(annotationName) || DocGlobalConstants.REQUEST_MAPPING_FULLY.equals(annotationName)) {
                if (annotation.getNamedParameter("value") != null) {
                    baseUrl = StringUtil.removeQuotes(annotation.getNamedParameter("value").toString());
                }
            }
        }
        List<JavaMethod> methods = cls.getMethods();
        List<ApiMethodDoc> methodDocList = new ArrayList<>(methods.size());
        int methodOrder = 0;
        for (JavaMethod method : methods) {
            if (method.isPrivate()) {
                continue;
            }
            if (StringUtil.isEmpty(method.getComment()) && apiConfig.isStrict()) {
                throw new RuntimeException("Unable to find comment for method " + method.getName() + " in " + cls.getCanonicalName());
            }
            methodOrder++;
            ApiMethodDoc apiMethodDoc = new ApiMethodDoc();
            apiMethodDoc.setOrder(methodOrder);
//            if(StringUtils.isNotEmpty(method.getComment())){
//                apiMethodDoc.setDesc(method.getComment().split("\\n|\\r\\n")[0]);
//            }
            apiMethodDoc.setDesc(method.getName());
            apiMethodDoc.setName(method.getName());
            apiMethodDoc.setContentType("application/json;charset=utf-8");
            String methodUid = clazName + "_" + method.getName(); //DocUtil.generateId(clazName + method.getName());
            apiMethodDoc.setMethodId(methodUid);
            String apiNoteValue = DocUtil.getNormalTagComments(method, DocTags.API_NOTE, cls.getName());
            if (StringUtil.isEmpty(apiNoteValue)) {
                apiNoteValue = method.getComment();
            }
            Map<String, String> authorMap = DocUtil.getParamsComments(method, DocTags.AUTHOR, cls.getName());
            String authorValue = String.join(", ", new ArrayList<>(authorMap.keySet()));
            if (apiConfig.isShowAuthor() && StringUtil.isNotEmpty(authorValue)) {
                apiMethodDoc.setAuthor(authorValue);
            }
            if (apiConfig.isShowAuthor() && StringUtil.isEmpty(authorValue)) {
                apiMethodDoc.setAuthor(classAuthor);
            }
            apiMethodDoc.setDetail(apiNoteValue);
            //handle request mapping
            RequestMapping requestMapping = new InterfaceRequestMappingHandler()
                    .handle(projectBuilder.getServerUrl(), baseUrl, method);
            //handle headers
            List<ApiReqHeader> apiReqHeaders = new ArrayList<>(); //new SpringMVCRequestHeaderHandler().handle(method);
            apiMethodDoc.setRequestHeaders(apiReqHeaders);
            if (Objects.nonNull(requestMapping)) {
                if (null != method.getTagByName(IGNORE)) {
                    continue;
                }
                apiMethodDoc.setType(requestMapping.getMethodType());
                apiMethodDoc.setUrl(requestMapping.getUrl());
                apiMethodDoc.setServerUrl(projectBuilder.getServerUrl());
                apiMethodDoc.setPath(requestMapping.getShortUrl());
                apiMethodDoc.setDeprecated(requestMapping.isDeprecated());
                // build request params
                List<ApiParam> requestParams = requestParams(method, DocTags.PARAM, projectBuilder);
                apiMethodDoc.setRequestParams(requestParams);
                // build request json
                ApiRequestExample requestExample = buildReqJson(method, apiMethodDoc, requestMapping.getMethodType(),
                        projectBuilder);
                String requestJson = requestExample.getExampleBody();
                // set request example detail
                apiMethodDoc.setRequestExample(requestExample);
                apiMethodDoc.setRequestUsage(requestJson == null ? requestExample.getUrl() : requestJson);
                // build response usage
                apiMethodDoc.setResponseUsage(JsonBuildHelper.buildReturnJson(method, projectBuilder));
                // build response params
                List<ApiParam> responseParams = buildReturnApiParams(method, projectBuilder);
                apiMethodDoc.setResponseParams(responseParams);

                apiMethodDoc.setReturnDesc(getReturnDesc(method, cls));

                List<ApiReqHeader> allApiReqHeaders;
                if (this.headers != null) {
                    allApiReqHeaders = Stream.of(this.headers, apiReqHeaders)
                            .flatMap(Collection::stream).distinct().collect(Collectors.toList());
                } else {
                    allApiReqHeaders = apiReqHeaders;
                }
                //reduce create in template
                apiMethodDoc.setHeaders(this.createDocRenderHeaders(allApiReqHeaders, apiConfig.isAdoc()));
                apiMethodDoc.setRequestHeaders(allApiReqHeaders);
                methodDocList.add(apiMethodDoc);
            }
        }
        return methodDocList;
    }

    private ApiParam getReturnDesc(JavaMethod method, JavaClass cls) {
        ApiReturn apiReturn = DocClassUtil.processReturnType(method.getReturnType().getGenericCanonicalName());
        String returnType = apiReturn.getSimpleName();
        String returnCommet = DocUtil.getFirstKeyAndValue(getParamsComments(method, "return", cls.getName()));

        if (returnCommet == null) {
            returnCommet = "";
        }

        ApiParam param = null;

        if (JavaClassValidateUtil.isCollection(returnType) || JavaClassValidateUtil.isArray(returnType)) {
            String[] gicNameArr = DocClassUtil.getSimpleGicName(apiReturn.getGenericCanonicalName());
            String gicName = gicNameArr[0];
            if (JavaClassValidateUtil.isArray(gicName)) {
                gicName = gicName.substring(0, gicName.indexOf("["));
            }
            String shortSimple = JavaClassValidateUtil.isPrimitive(gicName) ? processTypeNameForParams(gicName) : JavaClassUtil.getClassSimpleName(gicName);
            param = ApiParam.of()
                    .setField(apiReturn.getSimpleName())
                    .setDesc(returnCommet)
                    .setRequired(true)
                    .setType("array of " + shortSimple);

        } else {
            String shortSimple = JavaClassValidateUtil.isPrimitive(returnType) ? processTypeNameForParams(returnType) : returnType;

            param = ApiParam.of()
                    .setField(apiReturn.getSimpleName())
                    .setDesc(returnCommet)
                    .setRequired(true)
                    .setType(shortSimple);
        }

        return param;
    }

    private ApiRequestExample buildReqJson(JavaMethod method, ApiMethodDoc apiMethodDoc, String methodType,
                                           ProjectDocConfigBuilder configBuilder) {
        List<JavaParameter> parameterList = method.getParameters();

        ApiRequestExample requestExample = ApiRequestExample.builder();
        apiMethodDoc.setContentType(JSON_CONTENT_TYPE);

        String[] paths = apiMethodDoc.getPath().split(";");
        String url = apiMethodDoc.getServerUrl() + "/" + paths[0];
        url = UrlUtil.simplifyUrl(url);

        requestExample.setUrl(url);

        StringBuilder clientArgsList = new StringBuilder();

        if (parameterList.size() > 0) {
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{");
            for (int i = 0; i < parameterList.size(); i++) {
                JavaParameter parameter = parameterList.get(i);
                String paramName = parameter.getName();
                JavaType javaType = parameter.getType();
                String gicTypeName = javaType.getGenericCanonicalName();
                String simpleTypeName = parameter.getType().getGenericCanonicalName();
                String simpleName = parameter.getType().getValue().toLowerCase();
                String fullTypeName = parameter.getType().getFullyQualifiedName();

                String mockValue = DocUtil.getValByTypeAndFieldName(simpleTypeName, paramName);


                if (JavaClassValidateUtil.isPrimitive(simpleTypeName)) {
                    jsonBody.append(DocUtil.handleJsonStr(paramName))
                            .append(":")
                            .append(mockValue);
                } else {
                    String json = JsonBuildHelper.buildJson(simpleTypeName, gicTypeName, Boolean.FALSE, 0, new HashMap<>(), configBuilder);
                    jsonBody.append(DocUtil.handleJsonStr(paramName))
                            .append(":")
                            .append(json);
                }

                clientArgsList.append(paramName);

                if (i < parameterList.size() - 1 && parameterList.size() > 0) {
                    jsonBody.append(",");
                    clientArgsList.append(",");

                }

            }
            jsonBody.append("}");
            requestExample.setJsonBody(JsonFormatUtil.formatJson(jsonBody.toString())).setJson(true);
            String exampleBody = String.format("curl -X %s -H 'Content-Type: application/json; charset=utf-8' -i %s --data \n'%s'",
                    methodType,
                    url,
                    requestExample.getJsonBody()
            );
            requestExample.setExampleBody(exampleBody);
        } else {
            requestExample.setJsonBody("{ }").setJson(true);
            String exampleBody = String.format("curl -X %s -H 'Content-Type: application/json; charset=utf-8' -i %s --data '%s'",
                    methodType,
                    url,
                    requestExample.getJsonBody()
            );
            requestExample.setExampleBody(exampleBody);
        }

        return requestExample;
    }

    private String formDataToJson(List<FormData> formDataList) {
        Map<String, String> formDataMap = new IdentityHashMap();
        Iterator var2 = formDataList.iterator();

        while (var2.hasNext()) {
            FormData formData = (FormData) var2.next();
            if (formData.getKey().contains("[]")) {
                String key = formData.getKey().substring(0, formData.getKey().indexOf("["));
                formDataMap.put(key, formData.getValue());
            } else {
                formDataMap.put(formData.getKey(), formData.getValue());
            }
        }

        return JSON.toJSONString(formDataMap);
    }

    /**
     * obtain params comments
     *
     * @param javaMethod JavaMethod
     * @param tagName    java comments tag
     * @param className  class name
     * @return Map
     */
    public static Map<String, String> getParamsComments(final JavaMethod javaMethod, final String tagName, final String className) {
        List<DocletTag> paramTags = javaMethod.getTagsByName(tagName);
        Map<String, String> paramTagMap = new HashMap<>();
        for (DocletTag docletTag : paramTags) {
            String value = docletTag.getValue();
//            if (StringUtil.isEmpty(value) && StringUtil.isNotEmpty(className)) {
//                throw new RuntimeException("ERROR: #" + javaMethod.getName()
//                        + "() - bad @" + tagName + " javadoc from " + javaMethod.getDeclaringClass().getCanonicalName() + ", must be add comment if you use it.");
//            }
            String pName;
            String pValue;
            int idx = value.indexOf("\n");
            //existed \n
            if (idx > -1) {
                pName = value.substring(0, idx);
                pValue = value.substring(idx + 1);
            } else {
                pName = (value.contains(" ")) ? value.substring(0, value.indexOf(" ")) : value;
                pValue = value.contains(" ") ? value.substring(value.indexOf(' ') + 1) : DocGlobalConstants.NO_COMMENTS_FOUND;
            }
            paramTagMap.put(pName, pValue);
        }
        return paramTagMap;
    }

    private List<ApiParam> requestParams(final JavaMethod javaMethod, final String tagName, ProjectDocConfigBuilder builder) {
        boolean isStrict = builder.getApiConfig().isStrict();
        boolean isShowJavaType = builder.getApiConfig().getShowJavaType();

        Map<String, CustomRespField> responseFieldMap = new HashMap<>();
        Map<String, String> replacementMap = builder.getReplaceClassMap();
        String className = javaMethod.getDeclaringClass().getCanonicalName();
        Map<String, String> paramTagMap = getParamsComments(javaMethod, tagName, className);//DocUtil.getParamsComments(javaMethod, tagName, className);
        List<JavaParameter> parameterList = javaMethod.getParameters();
        if (parameterList.size() < 1) {
            return null;
        }
        boolean requestFieldToUnderline = builder.getApiConfig().isRequestFieldToUnderline();
        Set<String> jsonParamSet = this.jsonParamSet(parameterList);
        List<ApiParam> paramList = new ArrayList<>();
        int requestBodyCounter = 0;
        out:
        for (JavaParameter parameter : parameterList) {
            String paramName = parameter.getName();
            if (jsonParamSet.size() > 0 && !jsonParamSet.contains(paramName)) {
                continue;
            }
            String typeName = parameter.getType().getGenericCanonicalName();
            String simpleName = parameter.getType().getValue().toLowerCase();
            String fullTypeName = parameter.getType().getFullyQualifiedName();
            String commentClass = paramTagMap.get(paramName);

            if (JavaClassValidateUtil.isMvcIgnoreParams(typeName)) {
                continue out;
            }
            fullTypeName = DocClassUtil.rewriteRequestParam(fullTypeName);
            typeName = DocClassUtil.rewriteRequestParam(typeName);
            if (!paramTagMap.containsKey(paramName) && JavaClassValidateUtil.isPrimitive(fullTypeName) && isStrict) {
                throw new RuntimeException("ERROR: Unable to find javadoc @param for actual param \""
                        + paramName + "\" in method " + javaMethod.getName() + " from " + className);
            }
            String comment = this.paramCommentResolve(paramTagMap.get(paramName));
            if (requestFieldToUnderline) {
                paramName = StringUtil.camelToUnderline(paramName);
            }
            //file upload
            if (typeName.contains(DocGlobalConstants.MULTIPART_FILE_FULLY)) {
                ApiParam param = ApiParam.of().setField(paramName).setType("file")
                        .setDesc(comment).setRequired(true).setVersion(DocGlobalConstants.DEFAULT_VERSION);
                paramList.add(param);
                continue out;
            }
            JavaClass javaClass = builder.getJavaProjectBuilder().getClassByName(fullTypeName);
            List<JavaAnnotation> annotations = parameter.getAnnotations();
            List<String> groupClasses = JavaClassUtil.getParamGroupJavaClass(annotations);
            String strRequired = "true";
            for (JavaAnnotation annotation : annotations) {
                String annotationName = annotation.getType().getValue();
                if (SpringMvcAnnotations.REQUEST_HERDER.equals(annotationName)) {
                    continue out;
                }
                if (SpringMvcAnnotations.REQUEST_PARAM.equals(annotationName) ||
                        DocAnnotationConstants.SHORT_PATH_VARIABLE.equals(annotationName)) {
                    AnnotationValue annotationValue = annotation.getProperty(DocAnnotationConstants.VALUE_PROP);
                    if (null != annotationValue) {
                        paramName = StringUtil.removeQuotes(annotationValue.toString());
                    }
                    AnnotationValue annotationOfName = annotation.getProperty(DocAnnotationConstants.NAME_PROP);
                    if (null != annotationOfName) {
                        paramName = StringUtil.removeQuotes(annotationOfName.toString());
                    }
                    AnnotationValue annotationRequired = annotation.getProperty(DocAnnotationConstants.REQUIRED_PROP);
                    if (null != annotationRequired) {
                        strRequired = annotationRequired.toString();
                    }
                }
                if (SpringMvcAnnotations.REQUEST_BODY.equals(annotationName)) {
                    if (requestBodyCounter > 0) {
                        throw new RuntimeException("You have use @RequestBody Passing multiple variables  for method "
                                + javaMethod.getName() + " in " + className + ",@RequestBody annotation could only bind one variables.");
                    }
                    requestBodyCounter++;
                }
            }
            Boolean required = Boolean.parseBoolean(strRequired);
            if (JavaClassValidateUtil.isCollection(fullTypeName) || JavaClassValidateUtil.isArray(fullTypeName)) {
                String[] gicNameArr = DocClassUtil.getSimpleGicName(typeName);
                String gicName = gicNameArr[0];
                if (JavaClassValidateUtil.isArray(gicName)) {
                    gicName = gicName.substring(0, gicName.indexOf("["));
                }
                if (JavaClassValidateUtil.isPrimitive(gicName)) {
                    String shortSimple = processTypeNameForParams(gicName);
                    ApiParam param = ApiParam.of().setField(paramName).setDesc(comment)
                            .setRequired(required)
                            .setType("array of " + shortSimple);
                    paramList.add(param);
                } else {
                    if (requestBodyCounter > 0) {
                        //for json
                        paramList.addAll(ParamsBuildHelper.buildParams(gicNameArr[0], DocGlobalConstants.EMPTY, 0, "true", responseFieldMap, Boolean.FALSE, new HashMap<>(), builder, groupClasses));
                    } else {
//                        throw new RuntimeException("Spring MVC can't support binding Collection on method "
//                                + javaMethod.getName() + "Check it in " + javaMethod.getDeclaringClass().getCanonicalName());
                    }
                }
            } else if (JavaClassValidateUtil.isPrimitive(fullTypeName)) {
                ApiParam param = ApiParam.of().setField(paramName)
                        .setType(processTypeNameForParams(simpleName))
                        .setDesc(comment).setRequired(required).setVersion(DocGlobalConstants.DEFAULT_VERSION);
                paramList.add(param);
            } else if (JavaClassValidateUtil.isMap(fullTypeName)) {
                if (DocGlobalConstants.JAVA_MAP_FULLY.equals(typeName)) {
                    ApiParam apiParam = ApiParam.of().setField(paramName).setType("map")
                            .setDesc(comment).setRequired(required).setVersion(DocGlobalConstants.DEFAULT_VERSION);
                    paramList.add(apiParam);
                    continue out;
                }
                String[] gicNameArr = DocClassUtil.getSimpleGicName(typeName);
                paramList.addAll(ParamsBuildHelper.buildParams(gicNameArr[1], DocGlobalConstants.EMPTY, 0, "true", responseFieldMap, Boolean.FALSE, new HashMap<>(), builder, groupClasses));
            }
            //参数列表 当为枚举时
            else if (javaClass.isEnum()) {

                String o = JavaClassUtil.getEnumParams(javaClass);
                ApiParam param = ApiParam.of().setField(paramName)
                        .setType("enum").setDesc(StringUtil.removeQuotes(o)).setRequired(required).setVersion(DocGlobalConstants.DEFAULT_VERSION);
                paramList.add(param);
            } else {
                String processedType = isShowJavaType ? simpleName : processTypeNameForParams(simpleName.toLowerCase());
                ApiParam param = ApiParam.of().setField(paramName)
                        .setType(processedType)
                        .setDesc(comment)
                        .setRequired(required)
                        .setVersion(DocGlobalConstants.DEFAULT_VERSION);
                paramList.add(param);

                List<ApiParam> subParamList = ParamsBuildHelper.buildParams(typeName, "└─", 1, null, responseFieldMap, Boolean.FALSE, new HashMap<>(), builder, groupClasses);
                subParamList.forEach(apiParam -> apiParam.setRequired(true));
                paramList.addAll(subParamList);
            }
        }
        return paramList;
    }

    public Set<String> jsonParamSet(List<JavaParameter> parameterList) {
        Set<String> jsonParamSet = new HashSet<>();
        for (JavaParameter parameter : parameterList) {
            String paramName = parameter.getName();
            List<JavaAnnotation> annotations = parameter.getAnnotations();
            for (JavaAnnotation annotation : annotations) {
                String annotationName = annotation.getType().getValue();
//                if (SpringMvcAnnotations.REQUEST_BODY.equals(annotationName)) {
                jsonParamSet.add(paramName);
//                }
            }
        }
        return jsonParamSet;
    }


    @Override
    public List<ApiParam> buildReturnApiParams(JavaMethod method, ProjectDocConfigBuilder projectBuilder) {
        if (method.getReturns().isVoid()) {
            return null;
        }
        ApiReturn apiReturn = DocClassUtil.processReturnType(method.getReturnType().getGenericCanonicalName());
        String returnType = apiReturn.getGenericCanonicalName();
        String typeName = apiReturn.getSimpleName();
        if (this.ignoreReturnObject(typeName)) {
            return null;
        }
        if (JavaClassValidateUtil.isPrimitive(typeName)) {
            String processedName = projectBuilder.getApiConfig().getShowJavaType() ?
                    JavaClassUtil.getClassSimpleName(typeName) : processTypeNameForParams(typeName);
            return ParamsBuildHelper.primitiveReturnRespComment(processedName);
        }
        if (JavaClassValidateUtil.isCollection(typeName) || JavaClassValidateUtil.isArray(typeName)) {
            if (returnType.contains("<")) {
                String gicName = returnType.substring(returnType.indexOf("<") + 1, returnType.lastIndexOf(">"));
                if (JavaClassValidateUtil.isPrimitive(gicName)) {
                    return ParamsBuildHelper.primitiveReturnRespComment("array of " + processTypeNameForParams(gicName));
                }
                return ParamsBuildHelper.buildParams(gicName, "", 0, null, projectBuilder.getCustomRespFieldMap(),
                        Boolean.TRUE, new HashMap<>(), projectBuilder, null);
            } else if (returnType.contains("[")) {
                String gicName = returnType.substring(0, returnType.lastIndexOf("["));
                if (JavaClassValidateUtil.isPrimitive(gicName)) {
                    return ParamsBuildHelper.primitiveReturnRespComment("array of " + processTypeNameForParams(gicName));
                }
                return ParamsBuildHelper.buildParams(gicName, "", 0, null, projectBuilder.getCustomRespFieldMap(),
                        Boolean.TRUE, new HashMap<>(), projectBuilder, null);
            } else {
                return null;
            }
        }
        if (JavaClassValidateUtil.isMap(typeName)) {
            String[] keyValue = DocClassUtil.getMapKeyValueType(returnType);
            if (keyValue.length == 0) {
                return null;
            }
            if (JavaClassValidateUtil.isPrimitive(keyValue[1])) {
                return ParamsBuildHelper.primitiveReturnRespComment("key value");
            }
            return ParamsBuildHelper.buildParams(keyValue[1], "", 0, null, projectBuilder.getCustomRespFieldMap(),
                    Boolean.TRUE, new HashMap<>(), projectBuilder, null);
        }
        if (StringUtil.isNotEmpty(returnType)) {
            return ParamsBuildHelper.buildParams(returnType, "", 0, null, projectBuilder.getCustomRespFieldMap(),
                    Boolean.TRUE, new HashMap<>(), projectBuilder, null);
        }
        return null;
    }

}
