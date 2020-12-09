package com.peregrine.adaption.impl;

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

import com.peregrine.adaption.PerBase;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import java.util.Calendar;
import java.util.Objects;

import static com.peregrine.commons.util.PerConstants.*;

/**
 * Common Base Class for Peregrine Object Wrappers
 *
 * Created by Andreas Schaefer on 6/4/17.
 */
public abstract class PerBaseImpl implements PerBase {

    public static final String RESOURCE_MUST_BE_PROVIDED = "Resource must be provided";
    Logger logger = LoggerFactory.getLogger(getClass());

    /** The Resource this instance wraps **/
    private Resource resource;
    /** Reference to its JCR Content resource if the resource have on otherwise it is null **/
    private Resource jcrContent;

    public PerBaseImpl(Resource resource) {
        if(resource == null) {
            throw new IllegalArgumentException(RESOURCE_MUST_BE_PROVIDED);
        }
        this.resource = resource;
        jcrContent = getContentResource();
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public String getPath() {
        return resource.getPath();
    }

    @Override
    public String getName() {
        return resource.getName();
    }

    @Override
    public Calendar getLastModified() {
        return getContentProperty(JCR_LAST_MODIFIED, Calendar.class);
    }

    @Override
    public String getLastModifiedBy() {
        return getContentProperty(JCR_LAST_MODIFIED_BY, String.class);
    }

    @Override
    public boolean hasContent() {
        return getContentResource() != null;
    }

    @Override
    public boolean isValid() {
        return hasContent();
    }

    @Override
    public Resource getContentResource() {
        if(jcrContent == null) {
            jcrContent = resource.getChild(JCR_CONTENT);
        }
        return jcrContent;
    }

    @Override
    public ValueMap getProperties() {
        Resource content = getContentResource();
        return content != null ?
            content.getValueMap() :
            null;
    }

    @Override
    public ModifiableValueMap getModifiableProperties() {
        Resource content = getContentResource();
        return content != null ?
            content.adaptTo(ModifiableValueMap.class) :
            null;
    }

    @Override
    public <T> T getContentProperty(String propertyName, Class<T> type) {
        T answer = null;
        ValueMap properties = getProperties();
        if(properties != null) {
            answer = properties.get(propertyName, type);
        }
        return answer;
    }

    @Override
    public <T> T getContentProperty(String propertyName, T defaultValue) {
        T answer = null;
        if(defaultValue != null) {
            answer = getContentProperty(propertyName, (Class<T>) defaultValue.getClass());
        }
        return answer == null ?
            defaultValue :
            answer;
    }

    @Override
    public ValueMap getSiteProperties(){
        if(Objects.nonNull(getSiteResource()) && Objects.nonNull(getSiteResource().getChild(JCR_CONTENT))){
            return getSiteResource().getChild(JCR_CONTENT).getValueMap();
        }
        return null;
    }

    @Override
    public Resource getSiteResource(){
        return this.getSiteResource(this.resource);
    }

    private Resource getSiteResource(Resource resource){
        if (Objects.nonNull(this.resource)){
            try {
                NodeType nt = Objects.requireNonNull(resource.adaptTo(Node.class)).getPrimaryNodeType();
                if( nt.getName().equals(SITE_PRIMARY_TYPE)){
                    return resource;
                } else {
                    return getSiteResource(resource.getParent());
                }
            } catch (RepositoryException e) {
                logger.error("Error getting root per:Site resource ", e);
            }
        }
        return null;
    }

    @Override
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if(type.equals(Resource.class)) {
            return (AdapterType) resource;
        } else if(type.equals(ResourceResolver.class)) {
            return (AdapterType) resource.getResourceResolver();
        } else if(type.equals(Session.class)) {
            return (AdapterType) resource.getResourceResolver().adaptTo(Session.class);
        } else {
            return null;
        }
    }
}
