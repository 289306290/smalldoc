package com.github.liuhuagui.smalldoc.core.storer;

import java.util.List;

/**
 * 参数信息存储器
 *
 * @author KaiKang 799600902@qq.com
 */
public class ParamTagStorer {

    private String name;

    private String type;

    private List<FieldDocStorer> typeArguments;

    private String comment;
    /**
     * 参数是否必须
     */
    private boolean required;

    private Object example;

    private List<ParamTagStorer> fieldParamStorers;

    public ParamTagStorer(String name) {
        this.name = name;
    }

    public ParamTagStorer(String name, boolean required) {
        this.name = name;
        this.required = required;
    }

    public Object getExample() {
        return example;
    }

    public void setExample(Object example) {
        this.example = example;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public boolean isRequired() {
        return required;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<FieldDocStorer> getTypeArguments() {
        return typeArguments;
    }

    public void setTypeArguments(List<FieldDocStorer> typeArguments) {
        this.typeArguments = typeArguments;
    }

    public List<ParamTagStorer> getFieldParamStorers() {
        return fieldParamStorers;
    }

    public void setFieldParamStorers(List<ParamTagStorer> fieldParamStorers) {
        this.fieldParamStorers = fieldParamStorers;
    }

}
