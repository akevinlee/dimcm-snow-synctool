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

/**
 * ServiceNow Approval Waiter
 * @author klee@serena.com
 */
import com.microfocus.sync.servicenow.domain.ChangeRequest;
import com.microfocus.sync.servicenow.domain.ChangeTask;
import com.microfocus.sync.servicenow.exception.ServiceNowClientException;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CancellationException;

public class ApprovalWaiter implements Runnable {

    final static Logger logger = LoggerFactory.getLogger(ApprovalWaiter.class);

    ServiceNowClient sc;
    String callbackUrl;
    String callbackUsername;
    String callbackPassword;
    String executionId;
    String requestType;
    Long waitTime;
    Long maxPollCount;

    public ApprovalWaiter(ServiceNowClient sc, String callbackUrl, String callbackUsername, String callbackPassword,
                          String requestType, String executionId, String pollTime, String maxPolls) {
        this.sc = sc;
        if (callbackUrl.endsWith("/")) {
            this.callbackUrl = callbackUrl;
        } else {
            this.callbackUrl = callbackUrl + "/";
        }
        this.callbackUsername = callbackUsername;
        this.callbackPassword = callbackPassword;
        this.executionId = executionId;
        this.requestType = requestType;
        this.waitTime = Long.parseLong(pollTime);
        this.maxPollCount = Long.parseLong(maxPolls);
    }

    @Override
    public void run() {
        String approvalStatus = null;
        Long pollCount = 0L;

        while (pollCount < maxPollCount) {
            try {
                logger.debug("Waiting for {} milliseconds", waitTime);

                Thread.sleep(waitTime);
                synchronized (executionId) {
                    try {
                        if (requestType.equals("change_request")) {
                            ChangeRequest approved = sc.getChangeRequestById(executionId);
                            approvalStatus = approved.getApproval();
                            logger.debug("Change Request: {}, Approval Status {}", executionId, approvalStatus);
                        } else {
                            ChangeTask approved = sc.getChangeTaskById(executionId);
                            approvalStatus = approved.getApproval();
                            logger.debug("Change Task: {}, Approval Status {}", executionId, approvalStatus);
                        }
                    } catch (ServiceNowClientException ex) {
                        logger.error("Error checking approval status ({}) - {}", executionId, ex.getMessage());
                    }
                    // TODO: set reason for failure message
                    if (approvalStatus != null && approvalStatus.equals("Approved")) {
                        notifyRLC("COMPLETED");
                        break;
                    } else if (approvalStatus != null && approvalStatus.equals("Rejected")) {
                        notifyRLC("FAILED");
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                logger.error("ApprovalWaiter was interrupted: " + ex.getLocalizedMessage());
            }
            catch (CancellationException ex) {
                logger.error("ApprovalWaiter thread has been cancelled: ", ex.getLocalizedMessage());
            }

            pollCount++;
        }

        if (pollCount >= maxPollCount) {
            logger.debug("ApprovalWaiter exceeded maxPollCount...");
            notifyRLC("FAILED");
        }
        logger.debug("end ApprovalWait:run");

    }

    synchronized void notifyRLC(String status){
        try {
            String uri = callbackUrl + executionId + "/" + status;
            logger.debug("Start executing RLC PUT request to url=\"{}\"", uri);
            DefaultHttpClient rlcParams = new DefaultHttpClient();
            HttpPut put = new HttpPut(uri);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(callbackUsername, callbackPassword);
            put.addHeader(BasicScheme.authenticate(credentials, "US-ASCII", false));
            //put.addHeader("Content-Type", "application/x-www-form-urlencoded");
            //put.addHeader("Accept", "application/json");
            logger.info(credentials.toString());
            HttpResponse response = rlcParams.execute(put);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("HTTP Status Code: " + response.getStatusLine().getStatusCode());
            }
        } catch (IOException ex) {
            logger.error(ex.getLocalizedMessage());
        }
    }

}
