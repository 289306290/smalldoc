package com.github.liuhuagui.smalldoc.core;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.liuhuagui.smalldoc.core.constant.Constants;
import com.github.liuhuagui.smalldoc.core.storer.MappingDescStorer;
import com.github.liuhuagui.smalldoc.core.storer.MethodParamsStorer;
import com.github.liuhuagui.smalldoc.core.storer.ParamTagStorer;
import com.github.liuhuagui.smalldoc.util.Assert;
import com.github.liuhuagui.smalldoc.util.ParamFormatUtils;
import com.github.liuhuagui.smalldoc.util.TypeUtils;
import com.github.liuhuagui.smalldoc.util.Utils;
import com.sun.javadoc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.github.liuhuagui.smalldoc.core.constant.Constants.REQUEST_PARAM;


/**
 * 自定义Doclet。<br>
 * <b>注意：基于JDK1.8的Javadoc API，该API在JDK1.9被遗弃并由新的API支持，在不久的将来将被移除</b>
 *
 * @author KaiKang
 */
public class DefaultSmallDocletImpl extends SmallDoclet {

    public DefaultSmallDocletImpl(SmallDocContext smallDocContext) {
        super(smallDocContext);
        setDocLet(this);//将子类实例挂载到父类静态变量上
    }

    /**
     * 从{@link RootDoc}中解析文档信息
     *
     * @param root
     */
    @Override
    protected boolean process(RootDoc root) {
        handleClassDocs(root);
        return true;
    }

    /**
     * 处理所有类
     *
     * @param root
     */
    private void handleClassDocs(RootDoc root) {
        ClassDoc[] classes = root.classes();
        String nameRegex = nameRegex();
        for (ClassDoc classDoc : classes) {
            String name = classDoc.name();
            if (!name.endsWith(Constants.CONTROLLER)
                    && (nameRegex == null || !name.matches(nameRegex)))//配置你想要处理的类，提高程序性能
                continue;
            addClassDoc(classDoc);
        }
    }

    @Override
    protected JSONObject handleClassDoc(ClassDoc classDoc) {
        JSONObject classJSON = new JSONObject();
        classJSON.put("name", classDoc.name());
        classJSON.put("comment", classDoc.commentText());
        classJSON.put("authors", getAuthorsInfo(classDoc.tags("@author")));
        //处理Mapping
        JSONObject classMappingInfo = getMappingInfo(classDoc);
        classJSON.put("mapping", classMappingInfo);
        classJSON.put("methods", getMehodDocsInfo(classDoc, classMappingInfo));//由于classJSON除方法信息外有额外信息，所以使用methods统一管理方法信息
        return classJSON;
    }

    /**
     * 从@author标签中查询作者信息
     *
     * @param authorTags
     * @return
     */
    private JSONArray getAuthorsInfo(Tag[] authorTags) {
        JSONArray authorsJSONArray = new JSONArray();
        for (Tag tag : authorTags) {
            authorsJSONArray.add(tag.text());
        }
        return authorsJSONArray;
    }

    /**
     * 查询类中的方法信息
     *
     * @param classDoc         类文档
     * @param classMappingInfo 类的Mapping信息
     */
    private JSONArray getMehodDocsInfo(ClassDoc classDoc, JSONObject classMappingInfo) {
        JSONArray methodsJSONArray = new JSONArray();
        for (MethodDoc methodDoc : classDoc.methods()) {
            if (!methodDoc.isPublic())
                continue;
            handleMethodDoc(methodDoc, methodsJSONArray, classMappingInfo);
        }
        return methodsJSONArray;
    }

    /**
     * 处理单个方法
     *
     * @param methodDoc
     * @param methodsJSONArray
     * @param classMappingInfo
     */
    private void handleMethodDoc(MethodDoc methodDoc, JSONArray methodsJSONArray, JSONObject classMappingInfo) {
        setCurrentMethodSignature(methodDoc);//在上下文中设置当前解析的方法
        JSONObject methodMappingInfo = getMappingInfo(methodDoc);
        if (methodMappingInfo.isEmpty())//如果没有Mapping注解，则忽略此方法
            return;
        JSONObject methodJSON = new JSONObject();
        methodsJSONArray.add(methodJSON);

        methodJSON.put("name", methodDoc.name());
        methodJSON.put("comment", methodDoc.commentText());
        methodJSON.put("authors", getAuthorsInfo(methodDoc.tags("@author")));
        //处理Mapping
        methodJSON.put("mapping", handleMethodMappings(classMappingInfo, methodMappingInfo));
        //处理参数
        methodJSON.put("params", getParamDocsInfo(methodDoc));//由于methodJSON除参数信息外有额外信息，所以使用params统一管理参数信息
        //处理返回值
        methodJSON.put("returns", getReturnInfo(methodDoc));
    }

    /**
     * 根据Class的Mapping信息处理Method的Mapping
     *
     * @param classMappingInfo
     * @param methodMappingInfo
     * @return
     */
    private JSONObject handleMethodMappings(JSONObject classMappingInfo, JSONObject methodMappingInfo) {
        if (!classMappingInfo.isEmpty()) {
            String[] keys = {"method", "consumes", "produces"};
            String[] values;
            for (String key : keys) {
                if (Utils.isNotEmpty(values = (String[]) classMappingInfo.get(key)))
                    methodMappingInfo.put(key, values);
            }
        }
        methodMappingInfo.put("path", handleMappingPath((String[]) methodMappingInfo.get("path"), (String[]) classMappingInfo.get("path")));
        return methodMappingInfo;
    }

    /**
     * 根据Class的Mapping path得到Method最终的Mapping path
     *
     * @param methodMappingPaths
     * @param classMappingPaths  非空数组
     * @return
     */
    private ArrayList<String> handleMappingPath(String[] methodMappingPaths, String[] classMappingPaths) {
        ArrayList<String> finalPaths = new ArrayList<>();

        boolean methodPathsEmpty = Utils.isEmpty(methodMappingPaths);
        boolean classPathsEmpty = Utils.isEmpty(classMappingPaths);
        //如果类路径为空，直接使用方法路径（去除首部斜线）
        if (classPathsEmpty && !methodPathsEmpty) {
            for (String p1 : methodMappingPaths) {
                finalPaths.add(Utils.removeHeadSlashIfPresent(p1));
            }
        }

        //如果方法路径为空，用类路径作为最终路径（去除首部斜线）
        if (!classPathsEmpty && methodPathsEmpty) {
            for (String p0 : classMappingPaths) {
                finalPaths.add(Utils.removeHeadSlashIfPresent(p0));
            }
        }

        //如果类路径和方法路径都不为空
        if (!classPathsEmpty && !methodPathsEmpty) {
            for (String p0 : classMappingPaths) {
                //拼接类路径与方法路径作为最终路径
                for (String p1 : methodMappingPaths) {
                    finalPaths.add(Utils.unitePath(p0, p1));
                }
            }
        }
        return finalPaths;
    }


    private JSONObject getReturnInfo(MethodDoc methodDoc) {
        JSONObject returnJSON = new JSONObject();
        Type rtype = methodDoc.returnType();
        returnJSON.put("qtype", TypeUtils.inferBeanName(rtype));
        returnJSON.put("type", TypeUtils.getParamTypeWithDimension(rtype));//获取带维度的返回值
        //先处理类型参数，后面再去处理字段（保证TypeVariable的字段被处理）
        returnJSON.put("typeArguments", TypeUtils.getTypeArguments(rtype, this));

        //如果包含返回标签，则解析返回标签的注释
        Tag[] returnTags = methodDoc.tags("@return");
        if (Utils.isNotEmpty((returnTags)))
            returnJSON.put("comment", returnTags[0].text());

        //如果不是库类型，保留字段
        if (TypeUtils.isEntity(rtype, this)) {
            TypeUtils.addBean(rtype, this);
        }
        return returnJSON;
    }

    /**
     * 查询方法的所有参数信息<br>
     * <b>Note</b>: 如果你的参数在方法注释中不存在对应的@param，那么你的参数将被忽略，这有时可能也成为你所期望的。
     * 如果你的方法注释中存在了某个@param，而方法中不存该参数，将抛出断言异常。
     *
     * @param methodDoc
     * @return
     */
    private JSONArray getParamDocsInfo(MethodDoc methodDoc) {
        //处理参数
        JSONArray paramsJSONArray = new JSONArray();
        MethodParamsStorer methodParamsStorer = new MethodParamsStorer(methodDoc);
        ParamTag[] paramTags = methodDoc.paramTags();

        for (ParamTag paramTag : paramTags) {
            String paramName = paramTag.parameterName();
            ParamFormatUtils.formatParamDoc(this, methodParamsStorer.getParam(paramName), paramTag, paramsJSONArray);
        }
        return paramsJSONArray;
    }


    /**
     * 查询{@link ProgramElementDoc}的*Mapping注解信息
     *
     * @param elementDoc
     * @return
     */
    private JSONObject getMappingInfo(ProgramElementDoc elementDoc) {
        JSONObject mapping = new JSONObject();
        for (AnnotationDesc annotationDesc : elementDoc.annotations()) {
            MappingDescStorer mappingDescStorer = new MappingDescStorer(annotationDesc);
            String name = mappingDescStorer.name();
            if (name.endsWith("Mapping"))
                handleMappings(mapping, mappingDescStorer);
        }
        return mapping;
    }

    /**
     * 处理 @RequestMapping 信息
     *
     * @param mapping
     * @param mappingDescStorer
     */
    private void handleMappings(JSONObject mapping, MappingDescStorer mappingDescStorer) {
        mapping.put("method", getHttpMethod(mappingDescStorer));
        mapping.put("consumes", mappingDescStorer.getElementValue("consumes"));
        mapping.put("produces", mappingDescStorer.getElementValue("produces"));
        mapping.put("path", mappingDescStorer.getElementValue("value"));
    }

    /**
     * 获取Http Method
     *
     * @param mappingDescStorer
     * @return
     */
    private String[] getHttpMethod(MappingDescStorer mappingDescStorer) {
        String name = mappingDescStorer.name();
        if (Constants.REQUEST_MAPPING.equals(name)) {
            return mappingDescStorer.getElementValue("method");
        } else {
            return new String[]{name.substring(0, name.indexOf('M')).toUpperCase()};
        }
    }

}
