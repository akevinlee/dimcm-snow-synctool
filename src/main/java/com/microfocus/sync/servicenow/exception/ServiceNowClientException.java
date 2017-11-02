/*
 *
 * Copyright (c) 2016 SERENA Software, Inc. All Rights Reserved.
 *
 * This software is proprietary information of SERENA Software, Inc.
 * Use is subject to license terms.
 *
 * @author Kevin Lee
 */
package com.microfocus.sync.servicenow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TFS Release Manager Client Exceptions
 * @author klee@serena.com
 */
public class ServiceNowClientException extends Exception {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(ServiceNowClientException.class);

    public ServiceNowClientException() {
    }

    public ServiceNowClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceNowClientException(String message) {
        super(message);
    }

    public ServiceNowClientException(Throwable cause) {
        super(cause);
    }
}
