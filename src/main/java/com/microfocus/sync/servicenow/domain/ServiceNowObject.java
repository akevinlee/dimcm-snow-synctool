/*
 *
 * Copyright (c) 2016 SERENA Software, Inc. All Rights Reserved.
 *
 * This software is proprietary information of SERENA Software, Inc.
 * Use is subject to license terms.
 *
 * @author Kevin Lee
 */
package com.microfocus.sync.servicenow.domain;

import org.json.simple.JSONObject;

import java.io.Serializable;

/**
 * Base ServiceNow Object
 * @author klee@serena.com
 */
public class ServiceNowObject implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String type;
    private String number;
    private String title;
    private String description;
    private String createdOn;
    private String createdBy;
    private String updatedOn;
    private String updatedBy;
    private String state;
    private String url;

    public ServiceNowObject() {

    }

    public ServiceNowObject(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedOn() {
        return updatedOn;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getState() {
        return state;
    }

    public String getUrl() {
        return url;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedOn(String updatedOn) {
        this.updatedOn = updatedOn;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static Object getJSONValue(JSONObject obj, String key) {
        Object retObj = null;
        if (obj.containsKey(key)) {
            return obj.get(key);
        }
        return retObj;
    }

    @Override
    public String toString() {
        return super.toString();
    }

}