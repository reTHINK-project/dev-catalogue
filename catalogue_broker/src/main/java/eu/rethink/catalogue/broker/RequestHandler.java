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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * reTHINK specific Request Handler for requests on /.well-known/*
 */
public class RequestHandler {
    private final int HYPERTY_MODEL_ID = 1337;
    private final int PROTOSTUB_MODEL_ID = 1338;
    private final int HYPERTY_RUNTIME_MODEL_ID = 1339;

    private final String WELLKNOWN_PREFIX = "/.well-known/";
    private final String HYPERTY_TYPE_NAME = "hyperty";
    private final String PROTOSTUB_TYPE_NAME = "protostub";

    private LinkedHashMap<String, Integer> hypertyResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> hypertyToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<String, Integer> protostubResourceNameToID = new LinkedHashMap<>();
    private LinkedHashMap<String, String> protostubToInstanceMap = new LinkedHashMap<>();

    private LinkedHashMap<Client, String> clientToHypertyMap = new LinkedHashMap<>();
    private LinkedHashMap<Client, String> clientToProtostubMap = new LinkedHashMap<>();

    private LeshanServer server;
    public final Gson gson;
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);

    public RequestHandler(LeshanServer server) {
        this.server = server;

        // get LwM2mModel from modelprovider
        LwM2mModel customModel = server.getModelProvider().getObjectModel(null);


        // name:id map for
        // populate hypertyResourceNameToID
        ObjectEnabler hypertyEnabler = new ObjectsInitializer(customModel).create(HYPERTY_MODEL_ID);
        Map<Integer, ResourceModel> hypertyModel = hypertyEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : hypertyModel.entrySet()) {
            hypertyResourceNameToID.put(entry.getValue().name, entry.getKey());
        }
        LOG.debug("generated name:id map for hyperties: " + hypertyResourceNameToID);


        // name:id map for protostubs
        // populate protostubResourceNameToID
        ObjectEnabler protostubEnabler = new ObjectsInitializer(customModel).create(PROTOSTUB_MODEL_ID);
        Map<Integer, ResourceModel> protostubModel = protostubEnabler.getObjectModel().resources;
        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : protostubModel.entrySet()) {
            protostubResourceNameToID.put(entry.getValue().name, entry.getKey());
        }

        LOG.debug("generated name:id map for protostubs: " + protostubResourceNameToID);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(Client.class, new ClientSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mResponse.class, new ResponseSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeDeserializer());
        gsonBuilder.setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        this.gson = gsonBuilder.create();


        server.getClientRegistry().addListener(clientRegistryListener);

    }

    public ValueResponse handleGET(String path) {
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
            return createResponse(ResponseCode.BAD_REQUEST, response);
        } else if (pathParts.length == 1) { // hyperty | protostub only
            String type = pathParts[0];

            switch (type) {
                case HYPERTY_TYPE_NAME: {
                    return createResponse(this.gson.toJson(hypertyToInstanceMap.keySet().toArray()));
                }
                case PROTOSTUB_TYPE_NAME: {
                    return createResponse(this.gson.toJson(protostubToInstanceMap.keySet().toArray()));
                }
                default:
                    String response = "Invalid resource type. Please use: hyperty | protostub";
                    return createResponse(ResponseCode.BAD_REQUEST, response);
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
                    return createResponse(ResponseCode.BAD_REQUEST, response);
                }
            } else if (modelType.equals(PROTOSTUB_TYPE_NAME)) {
                resourceID = protostubResourceNameToID.get(resourceName);

                // resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, protostubResourceNameToID.keySet());
                    return createResponse(ResponseCode.BAD_REQUEST, response);
                }
            }

            // target should be: /<endpoint>/<objectID>/<instance>
            String target = null;
            switch (modelType) {
                case (HYPERTY_TYPE_NAME):
                    target = hypertyToInstanceMap.get(instanceName);
                    LOG.debug(String.format("target for hyperty '%s': %s", instanceName, target));
                    break;
                case (PROTOSTUB_TYPE_NAME):
                    target = protostubToInstanceMap.get(instanceName);
                    LOG.debug(String.format("target for protostub '%s': %s", instanceName, target));
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
                    return server.send(client, request);
                } else {
                    String error = String.format("Found target for '%s', but endpoint is invalid. Redundany error? Requested endpoint: %s", instanceName, targetPaths[0]);
                    LOG.warn(error);
                    return createResponse(ResponseCode.INTERNAL_SERVER_ERROR, error);
                }
            } else {
                String response = String.format("Could not find instance '%s'", instanceName);
                return createResponse(ResponseCode.NOT_FOUND, response);
            }

        }

    }

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

        private void checkClient(final Client client) {
            boolean foundHypertyLink = false;
            boolean foundProtostubLink = false;
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
                }
                // if both found, no need to keep checking
                if (foundHypertyLink && foundProtostubLink)
                    break;
            }

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
                                    int idFieldID = hypertyResourceNameToID.get("id");
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: get value of correct field ('id' for now)
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String hypertyName = resource.getValue().value.toString();
                                                hypertyToInstanceMap.put(hypertyName, "/" + client.getEndpoint() + "/" + HYPERTY_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + hypertyName + ": " + hypertyToInstanceMap.get(hypertyName));
                                                // also map hyperty name to client, for easy removal in case of client disconnect
                                                clientToHypertyMap.put(client, hypertyName);
                                            }

                                        }
                                    }
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
                                    int idFieldID = protostubResourceNameToID.get("id");
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        LOG.debug("checking resources of instance " + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                            // TODO: get value of correct field ('id' for now)
                                            // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                            if (resourceID == idFieldID) { // current resource is name field
                                                String protostubName = resource.getValue().value.toString();
                                                protostubToInstanceMap.put(protostubName, "/" + client.getEndpoint() + "/" + PROTOSTUB_MODEL_ID + "/" + instanceID);
                                                LOG.debug("Added to client map -> " + protostubName + ": " + protostubToInstanceMap.get(protostubName));
                                                // also map protostub name to client, for easy removal in case of client disconnect
                                                clientToProtostubMap.put(client, protostubName);
                                            }

                                        }
                                    }
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
            if (!foundHypertyLink && !foundProtostubLink) {
                LOG.debug("Client does not contain hyperties or protostubs");
            }
        }

        private void removeClient(Client client) {
            // check maps
            String hypertyName = clientToHypertyMap.remove(client);
            if (hypertyName != null) {
                LOG.debug("client contained hyperties, removing them from maps");
                String retVal = hypertyToInstanceMap.remove(hypertyName);
                if (retVal == null)
                    LOG.warn("unable to remove hyperty " + hypertyName + "from hypertyToInstanceMap!");
            }

            String protostubName = clientToProtostubMap.remove(client);
            if (protostubName != null) {
                LOG.debug("client contained protostubs, removing them from maps");
                String retVal = protostubToInstanceMap.remove(protostubName);
                if (retVal == null)
                    LOG.warn("unable to remove protostub " + protostubName + "from protostubToInstanceMap!");
            }
        }
    };

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

    private static ValueResponse createResponse(final String content) {
        return createResponse(ResponseCode.CONTENT, content);
    }

    public String encodeResponse(ValueResponse response) {
        return this.gson.toJson(response);
    }
}