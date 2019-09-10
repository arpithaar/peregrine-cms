package com.peregrine.admin.servlets;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */

import com.peregrine.admin.resource.AdminResourceHandler.DeletionResponse;
import com.peregrine.admin.resource.AdminResourceHandler.ManagementException;

import java.io.IOException;

import static com.peregrine.commons.util.PerConstants.DELETED;
import static com.peregrine.commons.util.PerConstants.NAME;
import static com.peregrine.commons.util.PerConstants.NODE_TYPE;
import static com.peregrine.commons.util.PerConstants.PARENT_PATH;
import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerConstants.STATUS;
import static com.peregrine.commons.util.PerConstants.TYPE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

@SuppressWarnings("serial")
public abstract class AbstractDeleteServlet extends AbstractAdminServlet {

    @Override
    protected String getStatus() { return DELETED; }

    protected abstract DeletionResponse doAction(Request request) throws ManagementException;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        String path = request.getParameter(PATH);
        try {
            DeletionResponse response = doAction(request);
            request.getResourceResolver().commit();
            JsonResponse answer = new JsonResponse()
                .writeAttribute(TYPE, getType())
                .writeAttribute(STATUS, getStatus());
            if(response != null) {
                answer
                    .writeAttribute(NAME, response.getName())
                    .writeAttribute(NODE_TYPE, response.getType())
                    .writeAttribute(PARENT_PATH, response.getParentPath());
            }
            enhanceResponse(answer, request);
            return answer;
        } catch (ManagementException e) {
            return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage(getFailureMessage()).setRequestPath(path).setException(e);
        }
    }
}
