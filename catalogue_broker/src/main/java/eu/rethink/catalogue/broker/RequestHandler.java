/*******************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 * <p/>
 * This contribution was based on the contribution from the leshan repository
 * on github (master branch as of 20151023).
 * <p/>
 * The copyright and list of contributors of the original, baseline contribution
 * is kept below
 * <p/>
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * <p/>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * <p/>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.html.
 * <p/>
 * Contributors:
 * Sierra Wireless - initial API and implementation
 *******************************************************************************/

/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package eu.rethink.catalogue.broker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import eu.rethink.catalogue.broker.json.ClientSerializer;
import eu.rethink.catalogue.broker.json.LwM2mNodeDeserializer;
import eu.rethink.catalogue.broker.json.LwM2mNodeSerializer;
import eu.rethink.catalogue.broker.json.ResponseSerializer;
import org.apache.commons.lang.StringUtils;
import org.eclipse.leshan.LinkObject;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.*;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * reTHINK specific Request Handler for requests on /.well-known/*
 */
public class RequestHandler {
    // model IDs that define the custom models inside model.json
    private final int HYPERTY_MODEL_ID = 1337;
    private final int PROTOSTUB_MODEL_ID = 1338;
    private final int RUNTIME_MODEL_ID = 1339;
    private final int SCHEMA_MODEL_ID = 1340;
    private final int SOURCEPACKAGE_MODEL_ID = 1350;

    private final String WELLKNOWN_PREFIX = "/.well-known/";
    private final String HYPERTY_TYPE_NAME = "hyperty";
    private final String PROTOSTUB_TYPE_NAME = "protocolstub";
    private final String RUNTIME_TYPE_NAME = "runtime";
    private final String SCHEMA_TYPE_NAME = "dataschema";
    private final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";
    private final String NAME_FIELD_NAME = "objectName";

    private final Map<Integer, ResourceModel> HYPERTYMODEL;
    private final Map<Integer, ResourceModel> PROTOSTUBMODEL;
    private final Map<Integer, ResourceModel> RUNTIMEMODEL;
    private final Map<Integer, ResourceModel> SCHEMAMODEL;
    private final Map<Integer, ResourceModel> SOURCEPACKAGEMODEL;

    private LinkedHashMap<String, Integer> hypertyResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> hypertyNameToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<String, Integer> protostubResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> protostubNameToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<String, Integer> runtimeResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> runtimeNameToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<String, Integer> schemaResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> schemaNameToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<String, Integer> sourcepackageResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> sourcepackageNameToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<Client, List<String>> clientToHypertyMap = new LinkedHashMap<>();
    private LinkedHashMap<Client, List<String>> clientToProtostubMap = new LinkedHashMap<>();
    private LinkedHashMap<Client, List<String>> clientToRuntimeMap = new LinkedHashMap<>();
    private LinkedHashMap<Client, List<String>> clientToSchemaMap = new LinkedHashMap<>();
    private LinkedHashMap<Client, List<String>> clientToSourcepackageMap = new LinkedHashMap<>();

    private LeshanServer server;
    public final Gson gson;
    public final JsonParser parser;
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);

    public RequestHandler(LeshanServer server) {
        this.server = server;

        // get LwM2mModel from modelprovider
        LwM2mModel customModel = server.getModelProvider().getObjectModel(null);


        // name:id map for
        // populate hypertyResourceNameToID
        ObjectEnabler hypertyEnabler = new ObjectsInitializer(customModel).create(HYPERTY_MODEL_ID);
        HYPERTYMODEL = hypertyEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : HYPERTYMODEL.entrySet()) {
            hypertyResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for hyperties: " + hypertyResourceNameToID);


        // name:id map for protostubs
        // populate protostubResourceNameToID
        ObjectEnabler protostubEnabler = new ObjectsInitializer(customModel).create(PROTOSTUB_MODEL_ID);
        PROTOSTUBMODEL = protostubEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : PROTOSTUBMODEL.entrySet()) {
            protostubResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for protostubs: " + protostubResourceNameToID);

        // name:id map for runtimes
        // populate runtimeResourceNameToID
        ObjectEnabler runtimeEnabler = new ObjectsInitializer(customModel).create(RUNTIME_MODEL_ID);
        RUNTIMEMODEL = runtimeEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : RUNTIMEMODEL.entrySet()) {
            runtimeResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for runtimes: " + runtimeResourceNameToID);

        // name:id map for schemas
        // populate schemaResourceNameToID
        ObjectEnabler schemaEnabler = new ObjectsInitializer(customModel).create(SCHEMA_MODEL_ID);
        SCHEMAMODEL = schemaEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : SCHEMAMODEL.entrySet()) {
            schemaResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for schemas: " + schemaResourceNameToID);

        // name:id map for
        // populate sourcepackageResourceNameToID
        ObjectEnabler sourcepackageEnabler = new ObjectsInitializer(customModel).create(SOURCEPACKAGE_MODEL_ID);
        SOURCEPACKAGEMODEL = sourcepackageEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : SOURCEPACKAGEMODEL.entrySet()) {
            sourcepackageResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for sourcepackages: " + sourcepackageResourceNameToID);

        // set up gson
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(Client.class, new ClientSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mResponse.class, new ResponseSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeDeserializer());
        gsonBuilder.setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        this.gson = gsonBuilder.create();
        this.parser = new JsonParser();

        server.getClientRegistry().addListener(clientRegistryListener);
    }

    public String handleGET(String path) {
        LOG.info("Handling GET for: " + path);
        // path should start with /.well-known/
        // but coap has no slash at the start, so check for it and prepend it if necessary.
        if (!path.startsWith("/"))
            path = "/" + path;

        // remove /.well-known/ from path
        path = StringUtils.removeStart(path, WELLKNOWN_PREFIX);
        LOG.debug("adapted path: " + path);
        // split path up
        String[] pathParts = StringUtils.split(path, '/');

        // example path: <endpoint>/<objectID>/<instance>/<resourceID>

        // TODO: no endpoint given -> return all clients or ask for more info?
        if (pathParts.length == 0) {
            String response = "Please provide resource type and (optional) name and (optional) resource name. Example: /hyperty/MyHyperty/sourceCode";
            ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
            return encodeErrorResponse(errorResp);
        } else if (pathParts.length == 1) { // hyperty | protostub | sourcepackage only
            String type = pathParts[0];

            switch (type) {
                case HYPERTY_TYPE_NAME: {
                    return this.gson.toJson(hypertyNameToInstanceMap.keySet());
                }
                case PROTOSTUB_TYPE_NAME: {
                    return this.gson.toJson(protostubNameToInstanceMap.keySet());
                }
                case RUNTIME_TYPE_NAME: {
                    return this.gson.toJson(runtimeNameToInstanceMap.keySet());
                }
                case SCHEMA_TYPE_NAME: {
                    return this.gson.toJson(schemaNameToInstanceMap.keySet());
                }
                case SOURCEPACKAGE_TYPE_NAME: {
                    return this.gson.toJson(sourcepackageNameToInstanceMap.keySet());
                }
                default:
                    String response = "Invalid resource type. Please use: hyperty | protostub | runtime | sourcepackage";
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
            }

        } else {
            String modelType = pathParts[0];
            String instanceName = pathParts[1];
            String resourceName = null;

            LOG.debug("modelType:    " + modelType);
            LOG.debug("instanceName: " + instanceName);

            if (pathParts.length > 2) {
                resourceName = pathParts[2];
                LOG.debug("resourceName: " + resourceName);
            }


            Integer resourceID = null;
            // check if resourceName is valid
            if (modelType.equals(HYPERTY_TYPE_NAME)) {
                resourceID = hypertyResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, hypertyResourceNameToID.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }
            } else if (modelType.equals(PROTOSTUB_TYPE_NAME)) {
                resourceID = protostubResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, protostubResourceNameToID.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }
            } else if (modelType.equals(RUNTIME_TYPE_NAME)) {
                resourceID = runtimeResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, runtimeResourceNameToID.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }
            } else if (modelType.equals(SCHEMA_TYPE_NAME)) {
                resourceID = schemaResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, schemaResourceNameToID.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }
            } else if (modelType.equals(SOURCEPACKAGE_TYPE_NAME)) {
                resourceID = sourcepackageResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, sourcepackageResourceNameToID.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }
            }

            // target should be: /<endpoint>/<objectID>/<instance>
            String target = null;
            switch (modelType) {
                case (HYPERTY_TYPE_NAME):
                    target = hypertyNameToInstanceMap.get(instanceName);
                    if (target == null && instanceName.equals("default")) {
                        LOG.debug("default hyperty requested, returning first in map");
                        target = hypertyNameToInstanceMap.values().iterator().next();
                    }
                    LOG.debug(String.format("target for hyperty '%s': %s", instanceName, target));
                    break;
                case (PROTOSTUB_TYPE_NAME):
                    target = protostubNameToInstanceMap.get(instanceName);
                    if (target == null && instanceName.equals("default")) {
                        LOG.debug("default stub requested, returning first in map");
                        target = protostubNameToInstanceMap.values().iterator().next();
                    }
                    LOG.debug(String.format("target for protocolstub '%s': %s", instanceName, target));
                    break;
                case (RUNTIME_TYPE_NAME):
                    target = runtimeNameToInstanceMap.get(instanceName);
                    if (target == null && instanceName.equals("default")) {
                        LOG.debug("default stub requested, returning first in map");
                        target = runtimeNameToInstanceMap.values().iterator().next();
                    } else {

                    }
                    LOG.debug(String.format("target for runtime '%s': %s", instanceName, target));
                    break;
                case (SCHEMA_TYPE_NAME):
                    target = schemaNameToInstanceMap.get(instanceName);

                    if (target == null && instanceName.equals("default")) {
                        LOG.debug("default stub requested, returning first in map");
                        target = schemaNameToInstanceMap.values().iterator().next();
                    }
                    LOG.debug(String.format("target for schema '%s': %s", instanceName, target));
                    break;
                case (SOURCEPACKAGE_TYPE_NAME):
                    target = sourcepackageNameToInstanceMap.get(instanceName);

                    if (target == null && instanceName.equals("default")) {
                        LOG.debug("default sourcepackage requested, returning first in map");
                        target = sourcepackageNameToInstanceMap.values().iterator().next();
                    }
                    LOG.debug(String.format("target for sourcepackage '%s': %s", instanceName, target));
                    break;
            }

            if (target != null) {
                if (resourceID != null)
                    target += "/" + resourceID;

                String[] targetPaths = StringUtils.split(target, "/");
                LOG.debug("checking endpoint: " + targetPaths[0]);
                Client client = server.getClientRegistry().get(targetPaths[0]);
                if (client != null) {
                    ReadRequest request = new ReadRequest(StringUtils.removeStart(target, "/" + targetPaths[0]));
                    return encodeResponse(server.send(client, request), modelType);
                } else {
                    String response = String.format("Found target for '%s', but endpoint is invalid. Redundany error? Requested endpoint: %s", instanceName, targetPaths[0]);
                    LOG.warn(response);
                    ValueResponse errorResp = createResponse(ResponseCode.INTERNAL_SERVER_ERROR, response);
                    return encodeErrorResponse(errorResp);
                }
            } else {
                String response = String.format("Could not find instance: %s", instanceName);
                ValueResponse errorResp = createResponse(ResponseCode.NOT_FOUND, response);
                return encodeErrorResponse(errorResp);
            }

        }

    }

    /**
     * Keeps track of currently registered clients.
     */
    private ClientRegistryListener clientRegistryListener = new ClientRegistryListener() {
        @Override
        public void registered(final Client client) {
            LOG.info("Client registered: " + client);
            try {
                checkClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while checking the client.", e);
//                e.printStackTrace();
            }

        }

        @Override
        public void updated(Client clientUpdated) {
            LOG.info("Client updated: " + clientUpdated);

            try {
                removeClient(clientUpdated);
            } catch (Exception e) {
                LOG.error("Something went wrong while removing the client.", e);
//                e.printStackTrace();
            }

            try {
                checkClient(clientUpdated);
            } catch (Exception e) {
                LOG.error("Something went wrong while checking the client.", e);
//                e.printStackTrace();
            }
        }

        @Override
        public void unregistered(Client client) {
            LOG.info("Client unregistered: " + client);

            try {
                removeClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while removing the client.", e);
//                e.printStackTrace();
            }
        }

        /**
         * Checks if client has one of our custom resources.
         */
        private void checkClient(final Client client) {
            boolean foundHypertyLink = false;
            boolean foundProtostubLink = false;
            boolean foundRuntimeLink = false;
            boolean foundSchemaLink = false;
            boolean foundSourcepackageLink = false;

            LOG.debug("checking object links of client: " + client);
            for (LinkObject link : client.getObjectLinks()) {
                String linkUrl = link.getUrl();
                LOG.debug("checking link: " + link.getUrl());
                if (!foundHypertyLink && linkUrl.startsWith("/" + HYPERTY_MODEL_ID + "/")) {
                    LOG.debug("found hyperty link: " + linkUrl + "; skipping additional links");
                    foundHypertyLink = true;
                } else if (!foundProtostubLink && linkUrl.startsWith("/" + PROTOSTUB_MODEL_ID + "/")) {
                    LOG.debug("found found protostub link: " + linkUrl + "; skipping additional links");
                    foundProtostubLink = true;
                } else if (!foundRuntimeLink && linkUrl.startsWith("/" + RUNTIME_MODEL_ID + "/")) {
                    LOG.debug("found found runtime link: " + linkUrl + "; skipping additional links");
                    foundRuntimeLink = true;
                } else if (!foundSchemaLink && linkUrl.startsWith("/" + SCHEMA_MODEL_ID + "/")) {
                    LOG.debug("found found schema link: " + linkUrl + "; skipping additional links");
                    foundSchemaLink = true;
                } else if (!foundSourcepackageLink && linkUrl.startsWith("/" + SOURCEPACKAGE_MODEL_ID + "/")) {
                    LOG.debug("found found sourcepackage link: " + linkUrl + "; skipping additional links");
                    foundSourcepackageLink = true;
                }
                // if all found, no need to keep checking
                if (foundHypertyLink && foundProtostubLink && foundRuntimeLink && foundSchemaLink && foundSourcepackageLink)
                    break;
            }

            // exit condition
            if (!foundHypertyLink && !foundProtostubLink && !foundRuntimeLink && !foundSchemaLink && !foundSourcepackageLink) {
                LOG.debug("Client does not contain hyperties, protostubs, hyperty runtimes, data schemas or sourcepackages");
                return;
            }

            // add hyperty to maps
            if (foundHypertyLink) {
                Thread hypertyRunner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(HYPERTY_MODEL_ID);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = hypertyResourceNameToID.get(NAME_FIELD_NAME);
                                    LinkedList<String> hypertyNames = new LinkedList<String>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String hypertyName = resource.getValue().value.toString();
                                                hypertyNameToInstanceMap.put(hypertyName, "/" + client.getEndpoint() + "/" + HYPERTY_MODEL_ID + "/" + instanceID);
                                                hypertyNames.add(hypertyName);
                                                LOG.debug("Added to client map -> " + hypertyName + ": " + hypertyNameToInstanceMap.get(hypertyName));
                                            }

                                        }
                                    }
                                    // map hyperty names to client, for easy removal in case of client disconnect
                                    clientToHypertyMap.put(client, hypertyNames);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance);
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource);
                                }
                            });
                        } else {
                            LOG.warn("Client contained hyperty links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                hypertyRunner.start();
                try {
                    hypertyRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // add protostub to maps
            if (foundProtostubLink) {
                Thread protostubRunner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(PROTOSTUB_MODEL_ID);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = protostubResourceNameToID.get(NAME_FIELD_NAME);
                                    LinkedList<String> protostubNames = new LinkedList<String>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String protostubName = resource.getValue().value.toString();
                                                protostubNameToInstanceMap.put(protostubName, "/" + client.getEndpoint() + "/" + PROTOSTUB_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + protostubName + ": " + protostubNameToInstanceMap.get(protostubName));
                                                protostubNames.add(protostubName);
                                            }

                                        }
                                    }
                                    // map protostub name to client, for easy removal in case of client disconnect
                                    clientToProtostubMap.put(client, protostubNames);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance);
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource);
                                }
                            });
                        } else {
                            LOG.warn("Client contained protostub links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                protostubRunner.start();
                try {
                    protostubRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            // add runtime to maps
            if (foundRuntimeLink) {
                Thread runtimeRunner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(RUNTIME_MODEL_ID);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = runtimeResourceNameToID.get(NAME_FIELD_NAME);
                                    LinkedList<String> runtimeNames = new LinkedList<String>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String runtimeName = resource.getValue().value.toString();
                                                runtimeNameToInstanceMap.put(runtimeName, "/" + client.getEndpoint() + "/" + RUNTIME_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + runtimeName + ": " + runtimeNameToInstanceMap.get(runtimeName));
                                                runtimeNames.add(runtimeName);
                                            }

                                        }
                                    }
                                    // map runtime name to client, for easy removal in case of client disconnect
                                    clientToRuntimeMap.put(client, runtimeNames);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance);
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource);
                                }
                            });
                        } else {
                            LOG.warn("Client contained runtime links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                runtimeRunner.start();
                try {
                    runtimeRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            // add schema to maps
            if (foundSchemaLink) {
                Thread schemaRunner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(SCHEMA_MODEL_ID);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = schemaResourceNameToID.get(NAME_FIELD_NAME);
                                    LinkedList<String> schemaNames = new LinkedList<String>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String schemaName = resource.getValue().value.toString();
                                                schemaNameToInstanceMap.put(schemaName, "/" + client.getEndpoint() + "/" + SCHEMA_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + schemaName + ": " + schemaNameToInstanceMap.get(schemaName));
                                                schemaNames.add(schemaName);
                                            }

                                        }
                                    }
                                    // map schema name to client, for easy removal in case of client disconnect
                                    clientToSchemaMap.put(client, schemaNames);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance);
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource);
                                }
                            });
                        } else {
                            LOG.warn("Client contained schema links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                schemaRunner.start();
                try {
                    schemaRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            // add sourcepackage to maps
            if (foundSourcepackageLink) {
                Thread sourcepackageRunner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(SOURCEPACKAGE_MODEL_ID);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = sourcepackageResourceNameToID.get(NAME_FIELD_NAME);
                                    LinkedList<String> sourcepackageNames = new LinkedList<String>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String sourcepackageName = resource.getValue().value.toString();
                                                sourcepackageNameToInstanceMap.put(sourcepackageName, "/" + client.getEndpoint() + "/" + SOURCEPACKAGE_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + sourcepackageName + ": " + sourcepackageNameToInstanceMap.get(sourcepackageName));
                                                sourcepackageNames.add(sourcepackageName);
                                            }

                                        }
                                    }
                                    // map sourcepackage name to client, for easy removal in case of client disconnect
                                    clientToSourcepackageMap.put(client, sourcepackageNames);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance);
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource);
                                }
                            });
                        } else {
                            LOG.warn("Client contained sourcepackage links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                sourcepackageRunner.start();
                try {
                    sourcepackageRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        /**
         * Tries to remove client and its resources from maps.
         */
        private void removeClient(Client client) {
            // check maps
            List<String> hypertyNames = clientToHypertyMap.remove(client);

            if (hypertyNames != null && hypertyNames.size() > 0) {
                LOG.debug("client contained hyperties, removing them from maps");
                for (String hypertyName : hypertyNames) {
                    String retVal = hypertyNameToInstanceMap.remove(hypertyName);
                    if (retVal == null)
                        LOG.warn("unable to remove hyperty " + hypertyName + "from hypertyNameToInstanceMap!");
                }

            }

            List<String> protostubNames = clientToProtostubMap.remove(client);

            if (protostubNames != null && protostubNames.size() > 0) {
                LOG.debug("client contained protostubs, removing them from maps");
                for (String protostubName : protostubNames) {
                    String retVal = protostubNameToInstanceMap.remove(protostubName);
                    if (retVal == null)
                        LOG.warn("unable to remove protostub " + protostubName + "from protostubNameToInstanceMap!");
                }
            }

            List<String> runtimeNames = clientToRuntimeMap.remove(client);

            if (runtimeNames != null && runtimeNames.size() > 0) {
                LOG.debug("client contained runtimes, removing them from maps");
                for (String runtimeName : runtimeNames) {
                    String retVal = runtimeNameToInstanceMap.remove(runtimeName);
                    if (retVal == null)
                        LOG.warn("unable to remove runtime " + runtimeName + "from runtimeNameToInstanceMap!");
                }
            }

            List<String> schemaNames = clientToSchemaMap.remove(client);

            if (schemaNames != null && schemaNames.size() > 0) {
                LOG.debug("client contained schemas, removing them from maps");
                for (String schemaName : schemaNames) {
                    String retVal = schemaNameToInstanceMap.remove(schemaName);
                    if (retVal == null)
                        LOG.warn("unable to remove schema " + schemaName + "from schemaNameToInstanceMap!");
                }
            }

            List<String> sourcepackageNames = clientToSourcepackageMap.remove(client);

            if (sourcepackageNames != null && sourcepackageNames.size() > 0) {
                LOG.debug("client contained sourcepackages, removing them from maps");
                for (String sourcepackageName : sourcepackageNames) {
                    String retVal = sourcepackageNameToInstanceMap.remove(sourcepackageName);
                    if (retVal == null)
                        LOG.warn("unable to remove sourcepackage " + sourcepackageName + "from sourcepackageNameToInstanceMap!");
                }
            }
        }
    };

    /**
     * Returns a ValueResponse based on the given code and content.
     * If no code is provided, ResponseCode.CONTENT will be used
     * @param code response code of the response
     * @param content payload of the response
     * @return generated ValueResponse
     */
    private static ValueResponse createResponse(ResponseCode code, final String content) {
        LOG.debug("creating response. code: " + code + ", content: " + content);
        ValueResponse response;
        if (code == null) {
            LOG.warn("no code for createResponse provided. Using ResponseCode.CONTENT");
            code = ResponseCode.CONTENT;
        }

        if (content == null)
            response = new ValueResponse(code);
        else {
            response = new ValueResponse(code, new LwM2mResource(0, Value.newStringValue(content)));
        }

        LOG.debug("created response: " + response);
        return response;
    }

    /**
     * Returns ValueResponse with the provided content as payload. Response code is always ResponseCode.CONTENT.
     * @param content payload of the response
     * @return generated ValueResponse
     */
    private static ValueResponse createResponse(final String content) {
        return createResponse(ResponseCode.CONTENT, content);
    }


    private String encodeErrorResponse(final ValueResponse response) {
        return encodeResponse(response, null, true);
    }

    private String encodeResponse(final ValueResponse response, final String modelType) {
        return encodeResponse(response, modelType, false);
    }


    /**
     * Parses a response to a json string.
     * @param response ValueResponse to be encoded to json
     * @return response as json
     */
    private String encodeResponse(final ValueResponse response, final String modelType, final boolean isError) {
//        LOG.debug("encoding response: " + response);

        Map<Integer, ResourceModel> model = null;
        if (modelType != null) {
            switch (modelType) {
                case (HYPERTY_TYPE_NAME):
                    model = HYPERTYMODEL;
                    break;
                case (PROTOSTUB_TYPE_NAME):
                    model = PROTOSTUBMODEL;
                    break;
                case (RUNTIME_TYPE_NAME):
                    model = RUNTIMEMODEL;
                    break;
                case (SCHEMA_TYPE_NAME):
                    model = SCHEMAMODEL;
                    break;
                case (SOURCEPACKAGE_TYPE_NAME):
                    model = SOURCEPACKAGEMODEL;
                    break;
            }
        }

        final LinkedHashMap<String, String> instanceMap = new LinkedHashMap<String, String>();

        final Map<Integer, ResourceModel> finalModel = model;

        // TODO: use proper exception type
//        LOG.debug("isError: " + isError + ", model: " + model);
        if (!isError && model == null) {
            throw new NullPointerException("could not resolve model type " + modelType);
        }

        final String[] result = {null};

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    response.getContent().accept(new LwM2mNodeVisitor() {
                        @Override
                        public void visit(LwM2mObject object) {
                            LOG.debug("visiting object");
                        }

                        @Override
                        public void visit(LwM2mObjectInstance instance) {
//                            LOG.debug("visiting instance: " + instance);
                            Map<Integer, LwM2mResource> resources = instance.getResources();
//                            LOG.debug("resources: " + resources);
                            for (Map.Entry<Integer, LwM2mResource> entry : resources.entrySet()) {
                                instanceMap.put(finalModel.get(entry.getKey()).name, (String) entry.getValue().getValue().value);
                            }

//                            LOG.debug("final instanceMap: " + instanceMap);

                            // TODO: modify sourcePackageURL here?
                            String sourcePackageURL = instanceMap.get("sourcePackageURL");
                            if (sourcePackageURL != null) {

                            }

                            result[0] = gson.toJson(instanceMap);
                        }

                        @Override
                        public void visit(LwM2mResource resource) {
//                            LOG.debug("visiting resource: " + resource);
                            if (isError) {
                                instanceMap.put(response.getCode().name(), (String) resource.getValue().value);

                                HashMap<String, Map<String, String>> errorMap = new HashMap<>(1);
                                errorMap.put("ERROR", instanceMap);
                                result[0] = gson.toJson(errorMap);
                            } else {
//                                if (finalModel.get(resource.getId()).name.equals("sourcePackage")) {
//                                    JsonElement obj = parser.parse((String) resource.getValue().value);
//                                    Set<Map.Entry<String, JsonElement>> entries = obj.getAsJsonObject().entrySet();
//                                    for (Map.Entry<String, JsonElement> entry : entries) {
//                                        instanceMap.put(entry.getKey(), entry.getValue().getAsString());
//                                    }
//                                    result[0] = gson.toJson(instanceMap);
//
//                                } else {
//                                    instanceMap.put(finalModel.get(resource.getId()).name, (String) resource.getValue().value);
//                                }
                                result[0] = (String) resource.getValue().value;
                            }

                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return result[0];
    }

}