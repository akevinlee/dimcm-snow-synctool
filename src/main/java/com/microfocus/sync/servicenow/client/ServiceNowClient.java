/*
 *
 * Copyright (c) 2016 SERENA Software, Inc. All Rights Reserved.
 *
 * This software is proprietary information of SERENA Software, Inc.
 * Use is subject to license terms.
 *
 * @author Kevin Lee
 */
package com.microfocus.sync.servicenow.client;

import com.microfocus.sync.servicenow.domain.Incident;
import com.microfocus.sync.servicenow.domain.ChangeRequest;
import com.microfocus.sync.servicenow.domain.ChangeTask;
import com.microfocus.sync.servicenow.exception.ServiceNowClientException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * ServiceNow Client
 * @author klee@serena.com
 */
@Component
public class ServiceNowClient {
    private static final Logger logger = LoggerFactory.getLogger(ServiceNowClient.class);

    public static String DEFAULT_HTTP_CONTENT_TYPE = "application/json";
    public static String SNOW_FIELDS  = "sys_id,type,impact,urgency,priority,description,number,short_description,state,approval,sys_created_by,sys_created_on,sys_updated_on,sys_updated_by";

    private String snowUrl;
    private String snowUsername;
    private String snowPassword;
    private String snowApiVersion;

    //================================================================================
    // Public Methods
    //================================================================================

    public ServiceNowClient() {
    }

    public ServiceNowClient(String snowUrl, String snowApiVersion, String username, String password) {
        this.createConnection(snowUrl, snowApiVersion, username, password);
    }

    public String getSnowUrl() {
        return snowUrl;
    }

    public void setSnowUrl(String url) {
        this.snowUrl = url;
    }

    public String getSnowApiVersion() {
        return snowApiVersion;
    }

    public void setSnowApiVersion(String snowApiVersion) {
        this.snowApiVersion = snowApiVersion;
    }

    public String getSnowUsername() {
        return snowUsername;
    }

    public void setSnowUsername(String username) {
        this.snowUsername = username;
    }

    public String getSnowPassword() {
        return snowPassword;
    }

    public void setSnowPassword(String password) {
        this.snowPassword = password;
    }

    /**
     * Create a new connection to ServiceNow.
     *
     * @param snowUrl  the url to Snow, e.g. https://servername
     * @param snowApiVersion  the version of the Snow REST API to use
     * @param username  the username of the Snow user
     * @param password  the password/private token of the Snow user
     */
    public void createConnection(String snowUrl, String snowApiVersion, String username, String password) {
        this.snowUrl = snowUrl;
        this.snowApiVersion = snowApiVersion;
        this.snowUsername = username;
        this.snowPassword = password;
    }

    /**
     * Get a list of Change Requests using a query.
     *
     * @param query  the id of the query to run
     * @param resultLimit  the maximum number of Change Requests to return
     * @return  a list of Change Requests
     * @throws ServiceNowClientException
     */
    public List<ChangeRequest> getChangeRequests(String query, String state, Integer resultLimit) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Requests using query \"{}\"", query);
        logger.debug("Limiting to state: " + state);
        logger.debug("Limiting results to: " + resultLimit.toString());

        String queryResponse = snowGetByTable("change_request", query, state, resultLimit);
        List<ChangeRequest> changeRequests = ChangeRequest.parse(queryResponse);

        return changeRequests;
    }

    /**
     * Get a list of Change Tasks using a query.
     *
     * @param query  the id of the query to run
     * @param resultLimit  the maximum number of Change Tasks to return
     * @return  a list of Change Tasks
     * @throws ServiceNowClientException
     */
    public List<ChangeTask> getChangeTasks(String query, String crNumber, String state, Integer resultLimit) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Tasks using query \"{}\"", query);
        logger.debug("Using Parent CR Number: " + crNumber);
        logger.debug("Limiting to state: " + state);
        logger.debug("Limiting results to: " + resultLimit.toString());

        String myQuery = query;
        if (crNumber != null && StringUtils.isNotEmpty(crNumber)) {
            myQuery += "^change_request.number="+crNumber;
        }

        String queryResponse = snowGetByTable("change_task", myQuery, state, resultLimit);
        List<ChangeTask> changeTasks = ChangeTask.parse(queryResponse);

        return changeTasks;
    }

    /**
     * Get a list of Incidents using a query.
     *
     * @param query  the id of the query to run
     * @param resultLimit  the maximum number of Incidents to return
     * @return  a list of Incidents
     * @throws ServiceNowClientException
     */
    public List<Incident> getIncidents(String query, String state, Integer resultLimit) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Incidents using query \"{}\"", query);
        logger.debug("Limiting to state: " + state);
        logger.debug("Limiting results to: " + resultLimit.toString());

        String queryResponse = snowGetByTable("incident", query, state, resultLimit);
        List<Incident> incidents = Incident.parse(queryResponse);

        return incidents;
    }

    /**
     * Get the details of a specific Change Request.
     *
     * @param crId  the sys_id of the Change Request
     * @return the Change Request if found
     * @throws ServiceNowClientException
     */
    public ChangeRequest getChangeRequestById(String crId) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Request by Id \"{}\"", crId);

        String queryResponse = snowGetById("change_request", crId);
        ChangeRequest cr = ChangeRequest.parseSingle(queryResponse);

        return cr;
    }

    /**
     * Get the details of a specific Change Task.
     *
     * @param ctId  the sys_id of the Change Task
     * @return the Change Task if found
     * @throws ServiceNowClientException
     */
    public ChangeTask getChangeTaskById(String ctId) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Task by Id \"{}\"", ctId);

        String queryResponse = snowGetById("change_task", ctId);
        ChangeTask ct = ChangeTask.parseSingle(queryResponse);

        return ct;
    }


    /**
     * Get the details of a specific Change Task.
     *
     * @param ctNumber  the change number of the Change Task
     * @return the Change Task if found
     * @throws ServiceNowClientException
     */
    public ChangeTask getChangeTaskByNumber(String ctNumber) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Task by Number \"{}\"", ctNumber);

        String queryResponse = snowGetByNumber("change_task", ctNumber);
        ChangeTask ct = ChangeTask.parse(queryResponse).get(0);

        return ct;
    }

    /**
     * Get the details of a specific Change Request.
     *
     * @param crNumber  the change number of the Change Request
     * @return the Change Request if found
     * @throws ServiceNowClientException
     */
    public ChangeRequest getChangeRequestByNumber(String crNumber) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Change Request by Number \"{}\"", crNumber);

        String queryResponse = snowGetByNumber("change_request", crNumber);
        ChangeRequest cr = ChangeRequest.parse(queryResponse).get(0);

        return cr;
    }

    /**
     * Get the approval status of a specific Change Request.
     *
     * @param crNumber  the change number of the Change Request
     * @return the approval status
     * @throws ServiceNowClientException
     */
    public String getChangeRequestApproval(String crNumber) throws ServiceNowClientException {
        logger.debug("Retrieving Approval Status of ServiceNow Change Request \"{}\"", crNumber);

        String queryResponse = snowGetByNumber("change_request", crNumber);
        ChangeRequest cr = ChangeRequest.parse(queryResponse).get(0);

        return cr.getApproval();
    }

    /**
     * Get the approval status of a specific Change Request.
     *
     * @param crNumber  the change number of the Change Request
     * @return the approval status
     * @throws ServiceNowClientException
     */
    public String getChangeRequestState(String crNumber) throws ServiceNowClientException {
        logger.debug("Retrieving Status of ServiceNow Change Request \"{}\"", crNumber);

        String queryResponse = snowGetByNumber("change_request", crNumber);
        ChangeRequest cr = ChangeRequest.parse(queryResponse).get(0);

        return cr.getState();
    }

    /**
     * Get the details of a specific Incident.
     *
     * @param incId  the sys_id of the Incident
     * @return the Incident if found
     * @throws ServiceNowClientException
     */
    public Incident getIncidentById(String incId) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Incident by Id \"{}\"", incId);

        String queryResponse = snowGetById("incident", incId);
        Incident inc = Incident.parseSingle(queryResponse);

        return inc;
    }

    /**
     * Get the details of a specific Incident.
     *
     * @param incNumber  the number of the Incident
     * @return the Incident if found
     * @throws ServiceNowClientException
     */
    public Incident getIncidentByNumber(String incNumber) throws ServiceNowClientException {
        logger.debug("Retrieving ServiceNow Incident by Number \"{}\"", incNumber);

        String queryResponse = snowGetByNumber("incident", incNumber);
        Incident inc = Incident.parse(queryResponse).get(0);

        return inc;
    }

    /*
     * Set the Approval status of a change request.
     *
     * @param crId  the identifier of the change request
     * @param status  the approval status to set
     * @return the updated Change Request
     * @throws ServiceNowClientException
     */
    public ChangeRequest setChangeRequestApproval(String crId, String status) throws ServiceNowClientException {
        logger.debug("Settings Change Request \"{}\" Approval Status to \"{}\"", crId, status);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("approval", status);

        String queryResponse = snowPutById("change_request", crId, jsonBody.toJSONString());
        logger.debug(queryResponse);

        ChangeRequest cr = ChangeRequest.parseSingle(queryResponse);
        return cr;
    }

    /*
     * Set the status of a change request.
     *
     * @param crId  the identifier of the change request
     * @param status  the status to set
     * @return the updated Change Request
     * @throws ServiceNowClientException
     */
    public ChangeRequest setChangeRequestStatus(String crId, String status) throws ServiceNowClientException {
        logger.debug("Settings Change Request \"{}\" Status to \"{}\"", crId, status);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("state", status);

        String queryResponse = snowPutById("change_request", crId, jsonBody.toJSONString());
        logger.debug(queryResponse);

        ChangeRequest cr = ChangeRequest.parseSingle(queryResponse);
        return cr;
    }

    /*
     * Set the status of a change task.
     *
     * @param ctId  the identifier of the change task
     * @param status  the status to set
     * @return the updated Change Task
     * @throws ServiceNowClientException
     */
    public ChangeTask setChangeTaskStatus(String ctId, String status) throws ServiceNowClientException {
        logger.debug("Settings Change Task \"{}\" Status to \"{}\"", ctId, status);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("state", status);

        String queryResponse = snowPutById("change_task", ctId, jsonBody.toJSONString());
        logger.debug(queryResponse);

        ChangeTask ct = ChangeTask.parseSingle(queryResponse);
        return ct;
    }

    /*
     * Create a Change Request.
     *
     * @param summary   summary of the change request
     * @param description   detailed description of the change request
     * @param type  the type of the change request
     * @param category  the category of the change request
     * @param priority  priority of the change request
     * @param risk  risk of the change request
     * @param impact   impact of the change request
     * @return the created Change Request
     * @throws ServiceNowClientException
     */
    public ChangeRequest createChangeRequest(String summary, String description, String type,
                                             String category, String priority, String risk, String impact) throws ServiceNowClientException {
        logger.debug("Creating Change Request \"{}\" of Type \"{}\"", summary, type);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("short_description", summary);
        jsonBody.put("description", description);
        jsonBody.put("type", type);
        jsonBody.put("category", category);
        jsonBody.put("priority", priority);
        jsonBody.put("risk", risk);
        jsonBody.put("impact", impact);

        String queryResponse = snowPost("change_request", jsonBody.toJSONString());
        logger.debug(queryResponse);

        ChangeRequest cr = ChangeRequest.parseSingle(queryResponse);
        return cr;
    }

    /*
     * Create a Change Task.
     *
     * @param crId  the identifier of the change request the task belongs to
     * @param summary   summary of the change task
     * @param description   detailed description of the change task
     * @param priority  priority of the change task
     * @param urgency   urgency of the change task
     * @return the created Change Task
     * @throws ServiceNowClientException
     */
    public ChangeTask createChangeTask(String crId, String summary, String description,
                                             String priority, String urgency) throws ServiceNowClientException {
        logger.debug("Creating Change Task \"{}\" for Change Request \"{}\"", summary, crId);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("short_description", summary);
        jsonBody.put("description", description);
        jsonBody.put("parent", crId);
        jsonBody.put("change_request", crId);
        jsonBody.put("urgency", urgency);
        jsonBody.put("priority", priority);

        String queryResponse = snowPost("change_task", jsonBody.toJSONString());
        logger.debug(queryResponse);

        ChangeTask ct = ChangeTask.parseSingle(queryResponse);
        return ct;
    }

    //================================================================================
    // Protected Methods
    //================================================================================

    /**
     * Execute a get request to ServiceNow.
     *
     * @param path  the path for the specific request
     * @param parameters  parameters to send with the query
     * @return String containing the response body
     * @throws ServiceNowClientException
     */
    protected String processGet(String path, String parameters) throws ServiceNowClientException {
        String uri = createUrl(path, parameters);

        logger.debug("Start executing ServiceNow GET request to url=\"{}\"", uri);

        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpGet getRequest = new HttpGet(uri);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(getSnowUsername(), getSnowPassword());
        getRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false) );
        getRequest.addHeader(HttpHeaders.CONTENT_TYPE, DEFAULT_HTTP_CONTENT_TYPE);
        getRequest.addHeader(HttpHeaders.ACCEPT, DEFAULT_HTTP_CONTENT_TYPE);
        String result = "";

        try {
            HttpResponse response = httpClient.execute(getRequest);
            if (response.getStatusLine().getStatusCode() != org.apache.http.HttpStatus.SC_OK) {
                throw createHttpError(response);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
            StringBuilder sb = new StringBuilder(1024);
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            result = sb.toString();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceNowClientException("Server not available", ex);
        } finally {
            httpClient.getConnectionManager().shutdown();
        }

        logger.debug("End executing ServiceNow GET request to url=\"{}\" and received this result={}", uri, result);

        return result;
    }

    /**
     * Execute a put request to ServiceNow.
     *
     * @param path  the path for the specific request
     * @param parameters  parameters to send with the query
     * @param body  the body to send with the request
     * @return String containing the response body
     * @throws ServiceNowClientException
     */
    protected String processPut(String path, String parameters, String body) throws ServiceNowClientException {
        String uri = createUrl(path, parameters);

        logger.debug("Start executing ServiceNow PUT request to url=\"{}\" with data: {}", uri, body);

        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPut putRequest = new HttpPut(uri);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(getSnowUsername(), getSnowPassword());
        putRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false) );
        putRequest.addHeader(HttpHeaders.CONTENT_TYPE, DEFAULT_HTTP_CONTENT_TYPE);
        putRequest.addHeader(HttpHeaders.ACCEPT, DEFAULT_HTTP_CONTENT_TYPE);

        try {
            putRequest.setEntity(new StringEntity(body,"UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceNowClientException("Error creating body for PUT request", ex);
        }
        String result = "";

        try {
            HttpResponse response = httpClient.execute(putRequest);
            if (response.getStatusLine().getStatusCode() != org.apache.commons.httpclient.HttpStatus.SC_OK &&
                    response.getStatusLine().getStatusCode() != org.apache.commons.httpclient.HttpStatus.SC_ACCEPTED) {
                throw createHttpError(response);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
            StringBuilder sb = new StringBuilder(1024);
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            result = sb.toString();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new ServiceNowClientException("Server not available", e);
        }

        logger.debug("End executing ServiceNow PUT request to url=\"{}\" and received this result={}", uri, result);

        return result;
    }

    /**
     * Execute a post request to ServiceNow.
     *
     * @param path  the path for the specific request
     * @param parameters  parameters to send with the query
     * @param body  the body to send with the request
     * @return String containing the response body
     * @throws ServiceNowClientException
     */
    protected String processPost(String path, String parameters, String body) throws ServiceNowClientException {
        String uri = createUrl(path, parameters);

        logger.debug("Start executing ServiceNow POST request to url=\"{}\" with data: {}", uri, body);

        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost postRequest = new HttpPost(uri);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(getSnowUsername(), getSnowPassword());
        postRequest.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false) );
        postRequest.addHeader(HttpHeaders.CONTENT_TYPE, DEFAULT_HTTP_CONTENT_TYPE);
        postRequest.addHeader(HttpHeaders.ACCEPT, DEFAULT_HTTP_CONTENT_TYPE);

        try {
            postRequest.setEntity(new StringEntity(body,"UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceNowClientException("Error creating body for POST request", ex);
        }
        String result = "";

        try {
            HttpResponse response = httpClient.execute(postRequest);
            if (response.getStatusLine().getStatusCode() != org.apache.commons.httpclient.HttpStatus.SC_OK && response.getStatusLine().getStatusCode() != org.apache.commons.httpclient.HttpStatus.SC_CREATED &&
                    response.getStatusLine().getStatusCode() != org.apache.commons.httpclient.HttpStatus.SC_ACCEPTED) {
                throw createHttpError(response);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
            StringBuilder sb = new StringBuilder(1024);
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            result = sb.toString();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new ServiceNowClientException("Server not available", e);
        }

        logger.debug("End executing ServiceNow POST request to url=\"{}\" and received this result={}", uri, result);

        return result;
    }

    /**
     * Create a ServiceNow URL from base and path.
     *
     * @param path  the path to the request
     * @param parameters  the parameters to send with the request
     * @return a String containing a complete Snow path
     */
    protected String createUrl(String path, String parameters) throws ServiceNowClientException {
        String base = getSnowUrl() + "/api/now/" + getSnowApiVersion();
        String query = parameters.trim();

        // trim and encode path
        path = path.trim().replaceAll(" ", "%20");
        // if path doesn't start with "/" add it
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        //System.out.println(base + path + query);
        return base + path + query;
    }

    //================================================================================
    // Private Methods
    //================================================================================

    private String snowGetByTable(String table, String query, String state, Integer limit) throws ServiceNowClientException {
        String stateQuery = "";
        if (state != null && StringUtils.isNotEmpty(state)) {
            stateQuery = "&state="+state;
        }
        String parameters = String.format("?sysparm_limit=%d&sysparm_display_value=true&sysparm_query=%s&sysparm_fields=%s%s",
                limit, URLEncoder.encode(query), URLEncoder.encode(SNOW_FIELDS), stateQuery);
        String queryResponse = processGet("/table/" + table, parameters);
        logger.debug(queryResponse);
        //System.out.println(queryResponse);
        return queryResponse;
    }

    private String snowGetById(String tableName, String id) throws ServiceNowClientException {
        String query = String.format("/table/%s/%s", tableName, id);
        String parameters = String.format("?sysparm_limit=%d&sysparm_display_value=true&sysparm_fields=%s",
                1, URLEncoder.encode(SNOW_FIELDS));
        String queryResponse = processGet(query, parameters);
        logger.debug(queryResponse);
        //System.out.println(queryResponse);
        return queryResponse;
    }

    private String snowGetByNumber(String tableName, String number) throws ServiceNowClientException {
        String query = String.format("/table/%s", tableName);
        String parameters = String.format("?sysparm_limit=%d&sysparm_display_value=true&sysparm_query=%s&sysparm_fields=%s",
                1, URLEncoder.encode("number=" + number), URLEncoder.encode(SNOW_FIELDS));
        String queryResponse = processGet(query, parameters);
        logger.debug(queryResponse);
        //System.out.println(queryResponse);
        return queryResponse;
    }

    private String snowPutById(String tableName, String id, String body) throws ServiceNowClientException {
        String query = String.format("/table/%s/%s", tableName, id);
        String parameters = String.format("?sysparm_display_value=true&sysparm_fields=%s",
                URLEncoder.encode(SNOW_FIELDS));
        String queryResponse = processPut(query, parameters, body);
        logger.debug(queryResponse);
        //System.out.println(queryResponse);
        return queryResponse;
    }

    private String snowPost(String tableName, String body) throws ServiceNowClientException {
        String query = String.format("/table/%s", tableName);
        String parameters = String.format("?sysparm_display_value=true&sysparm_fields=%s",
                URLEncoder.encode(SNOW_FIELDS));
        String queryResponse = processPost(query, parameters, body);
        logger.debug(queryResponse);
        //System.out.println(queryResponse);
        return queryResponse;
    }

    /**
     * Returns a ServiceNow Client specific Client Exception
     * @param response  the exception to throw
     * @return
     */
    private ServiceNowClientException createHttpError(HttpResponse response) {
        String message;
        try {
            StatusLine statusLine = response.getStatusLine();
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            StringBuffer responsePayload = new StringBuffer();
            // Read response until the end
            while ((line = rd.readLine()) != null) {
                responsePayload.append(line);
            }

            message = String.format(" request not successful: %d %s. Reason: %s", statusLine.getStatusCode(), statusLine.getReasonPhrase(), responsePayload);

            logger.debug(message);

            if (new Integer(HttpStatus.SC_UNAUTHORIZED).equals(statusLine.getStatusCode())) {
                return new ServiceNowClientException("ServiceNow: Invalid credentials provided.");
            } else if (new Integer(HttpStatus.SC_NOT_FOUND).equals(statusLine.getStatusCode())) {
                return new ServiceNowClientException("ServiceNow: Request URL not found.");
            } else if (new Integer(HttpStatus.SC_BAD_REQUEST).equals(statusLine.getStatusCode())) {
                return new ServiceNowClientException("ServiceNow: Bad request. " + responsePayload);
            }
        } catch (IOException e) {
            return new ServiceNowClientException("ServiceNow: Can't read response");
        }

        return new ServiceNowClientException(message);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    // Testing API
    static public void main(String[] args) {
        ServiceNowClient snow = new ServiceNowClient("https://dev35995.service-now.com", "v1", "admin", "BFZuZawFGhHq");

        ChangeRequest firstChangeRequest = null;
        ChangeTask firstChangeTask = null;
        Incident firstIncident = null;
        String titleFilter = "Java";
        String query = "short_descriptionLIKE" + titleFilter +
                "^ORdescriptionLIKE" + titleFilter +
                "^ORnumberLIKE" + titleFilter;

        System.out.println("Retrieving ServiceNow Change Requests...");
        List<ChangeRequest> changeRequests = null;
        try {
            changeRequests = snow.getChangeRequests(query, "New", 1);
            System.out.println("Found " + changeRequests.size() + " Change Requests");
            for (ChangeRequest cr : changeRequests) {
                if (firstChangeRequest == null) firstChangeRequest = cr;
                System.out.println("Found Change Request: " + cr.getNumber());
                System.out.println("Title: " + cr.getTitle());
                System.out.println("Description: " + cr.getDescription());
                System.out.println("State: " + cr.getState());
                System.out.println("URL: " + cr.getUrl());
            }
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        try {
            ChangeRequest cr = snow.getChangeRequestById(firstChangeRequest.getId());
            System.out.println("Found Change Request: " + cr.getNumber());
            System.out.println("Title: " + cr.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        try {
            ChangeRequest cr = snow.getChangeRequestByNumber(firstChangeRequest.getNumber());
            System.out.println("Found Change Request: " + cr.getNumber());
            System.out.println("Title: " + cr.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        System.out.println("Retrieving ServiceNow Change Tasks...");
        titleFilter = "Install";
        query = "short_descriptionLIKE" + titleFilter +
                "^ORdescriptionLIKE" + titleFilter +
                "^ORnumberLIKE" + titleFilter;
        List<ChangeTask> changeTasks = null;
        try {
            changeTasks = snow.getChangeTasks(query, firstChangeRequest.getNumber(), "Open", 10);
            System.out.println("Found " + changeTasks.size() + " Change Tasks");
            for (ChangeTask ct : changeTasks) {
                if (firstChangeTask == null) firstChangeTask = ct;
                System.out.println("Found Change Task: " + ct.getNumber());
                System.out.println("Title: " + ct.getTitle());
                System.out.println("Description: " + ct.getDescription());
                System.out.println("State: " + ct.getState());
                System.out.println("URL: " + ct.getUrl());
            }
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

       try {
            ChangeTask ct = snow.getChangeTaskById(firstChangeTask.getId());
            System.out.println("Found Change Task: " + ct.getNumber());
            System.out.println("Title: " + ct.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        try {
            ChangeTask ct = snow.getChangeTaskByNumber(firstChangeTask.getNumber());
            System.out.println("Found Change Task: " + ct.getNumber());
            System.out.println("Title: " + ct.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        System.out.println("Retrieving ServiceNow Incidents...");
        titleFilter = "password";
        query = "short_descriptionLIKE" + titleFilter +
                "^ORdescriptionLIKE" + titleFilter +
                "^ORnumberLIKE" + titleFilter;
        List<Incident> incidents = null;
        try {
            incidents = snow.getIncidents(query, "Closed", 10);
            System.out.println("Found " + incidents.size() + " Incidents");
            for (Incident i : incidents) {
                if (firstIncident == null) firstIncident = i;
                System.out.println("Found Incident: " + i.getNumber());
                System.out.println("Title: " + i.getTitle());
                System.out.println("Description: " + i.getDescription());
                System.out.println("State: " + i.getState());
                System.out.println("Type: " + i.getType());
                System.out.println("URL: " + i.getUrl());
            }
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        try {
            Incident inc = snow.getIncidentById(firstIncident.getId());
            System.out.println("Found Incident: " + inc.getNumber());
            System.out.println("Title: " + inc.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        try {
            Incident inc = snow.getIncidentByNumber(firstIncident.getNumber());
            System.out.println("Found Incident: " + inc.getNumber());
            System.out.println("Title: " + inc.getTitle());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        System.out.println("Creating Change Request");
        String changeNumber = "";
        String changeId = "";
        try {
            ChangeRequest cr = snow.createChangeRequest("my cr", "its description",
                    "Normal", "Software", "3 - Moderate", "High",  "3 - Low");
            System.out.println("Created Change Request: " + cr.getNumber());
            System.out.println("Title: " + cr.getTitle());
            System.out.println("Description: " + cr.getDescription());
            System.out.println("State: " + cr.getState());
            System.out.println("URL: " + cr.getUrl());
            changeId = cr.getId();
            changeNumber = cr.getNumber();
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        String approvalStatus = null;
        System.out.println("Checking Approval State of " + changeNumber);
        int pollCount = 0;
        while (pollCount < 100) {
            try {
                approvalStatus = snow.getChangeRequestApproval(changeNumber);
                System.out.println("Approval Status = " + approvalStatus);
            } catch (ServiceNowClientException e) {
                logger.debug ("Error checking approval status ({}) - {}", changeNumber, e.getMessage());
            }
            if (approvalStatus != null && (approvalStatus.equals("Approved") ||
                    approvalStatus.equals("Rejected"))) {
                break;
            } else {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) { }
            }

            pollCount++;
        }

        if (approvalStatus != null && approvalStatus.equals("Approved")) {
            System.out.println("Change Request " + changeNumber + " has been approved");
        } else {
            System.out.println("Change Request " + changeNumber + " has been rejected or its status cannot be retrieved.");
        }


        System.out.println("Checking State of " + changeNumber);
        try {
            ChangeRequest cr = snow.getChangeRequestByNumber(changeNumber);
            System.out.println("Status = " + cr.getState());
            cr = snow.setChangeRequestStatus(cr.getId(), "Closed");
            System.out.println("Status = " + cr.getState());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        System.out.println("Creating Change Task");
        String changeTaskNumber = "";
        String changeTaskId = "";
        try {
            ChangeTask ct = snow.createChangeTask(firstChangeRequest.getId(),
                    "my ct", "its description", "1 - Critical", "3 - Low");
            System.out.println("Created Change Task: " + ct.getNumber());
            System.out.println("Title: " + ct.getTitle());
            System.out.println("Description: " + ct.getDescription());
            System.out.println("State: " + ct.getState());
            System.out.println("URL: " + ct.getUrl());
            changeTaskId = ct.getId();
            changeTaskNumber = ct.getNumber();
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

        System.out.println("Checking State of " + changeTaskNumber);
        try {
            ChangeTask ct = snow.getChangeTaskById(changeTaskId);
            System.out.println("Status = " + ct.getState());
            ct = snow.setChangeTaskStatus(ct.getId(), "Closed Complete");
            System.out.println("Status = " + ct.getState());
        } catch (ServiceNowClientException e) {
            System.out.print(e.toString());
        }

    }

}
