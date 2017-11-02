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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ServiceNow Change Task Object
 * @author klee@serena.com
 */
public class ChangeTask extends ServiceNowObject {

    private static final long serialVersionUID = 1L;

    private final static Logger logger = LoggerFactory.getLogger(ChangeTask.class);

    private String impact;
    private String urgency;
    private String priority;
    private String approval;

    public ChangeTask() {

    }

    public ChangeTask(String id, String title, String description, String status, String url) {
        this.setId(id);
        this.setTitle(title);
        this.setDescription(description);
        this.setState(status);
        this.setUrl(url);
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getApproval() {
        return approval;
    }

    public void setApproval(String approval) {
        this.approval = approval;
    }

    public static ChangeTask parseSingle(String options) {
        JSONParser parser = new JSONParser();
        try {
            Object parsedObject = parser.parse(options);
            ChangeTask changeTask = parseSingle((JSONObject)getJSONValue((JSONObject) parsedObject, "result"));
            return changeTask;
        } catch (ParseException e) {
            logger.error("Error while parsing input JSON - " + options, e);
        }
        return null;
    }

    public static List<ChangeTask> parse(String options) {
        List<ChangeTask> ctList = new ArrayList<>();
        JSONParser parser = new JSONParser();
        try {
            Object parsedObject = parser.parse(options);
            JSONArray jsonArray = (JSONArray) getJSONValue((JSONObject) parsedObject, "result");
            for (Object object : jsonArray) {
                ChangeTask ctObj = parseSingle((JSONObject)object);
                ctList.add(ctObj);
            }
        } catch (ParseException e) {
            logger.error("Error while parsing input JSON - " + options, e);
        }
        return ctList;
    }

    public static ChangeTask parseSingle(JSONObject jsonObject) {
        ChangeTask ctObj = new ChangeTask((String) getJSONValue(jsonObject, "sys_id"),
                (String) getJSONValue(jsonObject, "short_description"),
                (String) getJSONValue(jsonObject, "description"),
                (String) getJSONValue(jsonObject, "state"),
                //https://demo002.service-now.com/nav_to.do?uri=change_task.do?sys_id=a8fa0977c0a80a692a25f32a09fb37ab
                "uri=change_task.do?sys_id=" + (String) getJSONValue(jsonObject, "sys_id")
                );
        ctObj.setNumber((String) getJSONValue(jsonObject, "number"));
        ctObj.setType((String) getJSONValue(jsonObject, "type"));
        ctObj.setImpact((String) getJSONValue(jsonObject, "impact"));
        ctObj.setUrgency((String) getJSONValue(jsonObject, "urgency"));
        ctObj.setPriority((String) getJSONValue(jsonObject, "priority"));
        ctObj.setApproval((String) getJSONValue(jsonObject, "approval"));
        ctObj.setCreatedOn((String) getJSONValue(jsonObject, "sys_created_on"));
        ctObj.setCreatedBy((String) getJSONValue(jsonObject, "sys_created_by"));
        ctObj.setUpdatedOn((String) getJSONValue(jsonObject, "sys_updated_on"));
        ctObj.setUpdatedBy((String) getJSONValue(jsonObject, "sys_updated_by"));

        return ctObj;
    }

    @Override
    public String toString() {
        return "ChangeTask{" + "id=" + super.getId() + ", " +
                "number=" + super.getNumber() +
                "title=" + super.getTitle() +
        '}';
    }
}