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
 * ServiceNow Change Request Object
 * @author klee@serena.com
 */
public class ChangeRequest extends ServiceNowObject {

    private static final long serialVersionUID = 1L;

    private final static Logger logger = LoggerFactory.getLogger(ChangeRequest.class);

    private String impact;
    private String urgency;
    private String priority;
    private String approval;

    public ChangeRequest() {

    }

    public ChangeRequest(String id, String title, String description, String status, String url) {
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

    public static ChangeRequest parseSingle(String options) {
        JSONParser parser = new JSONParser();
        try {
            Object parsedObject = parser.parse(options);
            ChangeRequest changeRequest = parseSingle((JSONObject)getJSONValue((JSONObject) parsedObject, "result"));
            return changeRequest;
        } catch (ParseException e) {
            logger.error("Error while parsing input JSON - " + options, e);
        }
        return null;
    }

    public static List<ChangeRequest> parse(String options) {
        List<ChangeRequest> crList = new ArrayList<>();
        JSONParser parser = new JSONParser();
        try {
            Object parsedObject = parser.parse(options);
            JSONArray jsonArray = (JSONArray) getJSONValue((JSONObject) parsedObject, "result");
            for (Object object : jsonArray) {
                ChangeRequest crObj = parseSingle((JSONObject)object);
                crList.add(crObj);
            }
        } catch (ParseException e) {
            logger.error("Error while parsing input JSON - " + options, e);
        }
        return crList;
    }

    public static ChangeRequest parseSingle(JSONObject jsonObject) {
        ChangeRequest crObj = new ChangeRequest((String) getJSONValue(jsonObject, "sys_id"),
                (String) getJSONValue(jsonObject, "short_description"),
                (String) getJSONValue(jsonObject, "description"),
                (String) getJSONValue(jsonObject, "state"),
                //https://demo002.service-now.com/nav_to.do?uri=change_request.do?sys_id=a8fa0977c0a80a692a25f32a09fb37ab
                "uri=change_request.do?sys_id=" + (String) getJSONValue(jsonObject, "sys_id")
                );
        crObj.setNumber((String) getJSONValue(jsonObject, "number"));
        crObj.setType((String) getJSONValue(jsonObject, "type"));
        crObj.setImpact((String) getJSONValue(jsonObject, "impact"));
        crObj.setUrgency((String) getJSONValue(jsonObject, "urgency"));
        crObj.setPriority((String) getJSONValue(jsonObject, "priority"));
        crObj.setApproval((String) getJSONValue(jsonObject, "approval"));
        crObj.setCreatedOn((String) getJSONValue(jsonObject, "sys_created_on"));
        crObj.setCreatedBy((String) getJSONValue(jsonObject, "sys_created_by"));
        crObj.setUpdatedOn((String) getJSONValue(jsonObject, "sys_updated_on"));
        crObj.setUpdatedBy((String) getJSONValue(jsonObject, "sys_updated_by"));

        return crObj;
    }

    @Override
    public String toString() {
        return "ChangeRequest{" + "id=" + super.getId() + ", " +
                "number=" + super.getNumber() +
                "title=" + super.getTitle() +
        '}';
    }
}