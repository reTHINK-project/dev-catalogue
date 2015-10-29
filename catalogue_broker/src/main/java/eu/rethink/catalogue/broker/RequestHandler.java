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
import eu.rethink.catalogue.broker.json.LwM2mNodeSerializer;
import eu.rethink.catalogue.broker.json.LwM2mNodeDeserializer;
import eu.rethink.catalogue.broker.json.ResponseSerializer;
import org.apache.commons.lang.StringUtils;
import org.eclipse.leshan.LinkObject;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.node.*;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * reTHINK specific Request Handler for requests on /.well-known/*
 */
public class RequestHandler {
    private String WELLKNOWN_PREFIX = "/.well-known/";
    private int HYPERTY_OBJ_ID = 1337;
    private int PROTOCOLSTUB_OBJ_ID = 1338;
    private LeshanServer server;
    private final Gson gson;
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);
    private HashMap<String, Integer> hypertyResourceNameToID = new HashMap<>();

    public RequestHandler(LeshanServer server) {
        this.server = server;

        // populate hypertyResourceNameToID
        hypertyResourceNameToID.put("uuid", 0);
        hypertyResourceNameToID.put("name", 1);
        hypertyResourceNameToID.put("src_url", 2);
        hypertyResourceNameToID.put("code", 3);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(Client.class, new ClientSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mResponse.class, new ResponseSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeDeserializer());
        gsonBuilder.setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        this.gson = gsonBuilder.create();



        server.getClientRegistry().addListener(clientRegistryListener);

    }


    public String handleGET(String path) {
        LOG.info("Handling GET for: " + path);
        // remove /.well-known/ from path
        path = StringUtils.removeStart(path, WELLKNOWN_PREFIX);
        LOG.info("adapted path: " + path);
        // split path up
        String[] pathParts = StringUtils.split(path, '/');

        // no endpoint given -> return all clients
        if (pathParts.length == 0) {
//            Collection<Client> clients = server.getClientRegistry().allClients();
//            String json = this.gson.toJson(clients.toArray(new Client[]{}));
            return "Please provide resource type and name. Example: /hyperty/MyHyperty";
        } else if (pathParts.length == 1) {
            // hyperty | protocolstub only
            String type = pathParts[0];

            // TODO: return only hyperty clients or protocolstub clients

            if (type.equals("hyperty")) {
                String json = this.gson.toJson(clientToHypertyMap.keySet().toArray());
                return json;
            } else if (type.equals("protocolstub")) {
                String json = this.gson.toJson(clientToProtocolstubMap.keySet().toArray());
                return json;
            } else {
                    return "Invalid resource type. Please use: hyperty | protocolstub";
            }

        } else {
            String type = pathParts[0];
            String hypertyName = pathParts[1];
            String resourceName = null;

            if (pathParts.length > 2) {
                resourceName = pathParts[2];
            }

            LOG.info("resourcetype: " + type);
            LOG.info("hypertyname: " + hypertyName);

            LOG.info("resourceName: " + resourceName);
            Integer resourceID = null;
            // check if resourceName is valid
            if (type.equals("hyperty")) {
                resourceID = hypertyResourceNameToID.get(resourceName);
            } else if (type.equals("protocolstub")) {
                // TODO: get ID for protocolstub resources
            }

            if (resourceName != null && resourceID == null) {
                return String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, hypertyResourceNameToID.keySet());
            }

            // target should be: /<endpoint>/<objectID>/<instance>
            String target = hypertyToInstanceMap.get(hypertyName);
            LOG.info(String.format("target for hyperty '%s': %s", hypertyName, target));

            if (target != null) {

                if (resourceID != null)
                    target += "/" + resourceID;

                String[] targetPaths = StringUtils.split(target, "/");
                LOG.info("checking endpoint: " + targetPaths[0]);
                Client client = server.getClientRegistry().get(targetPaths[0]);
                if (client != null) {
                    ReadRequest request = new ReadRequest(StringUtils.removeStart(target, "/" + targetPaths[0]));
                    LwM2mResponse response = server.send(client, request);
                    return this.gson.toJson(response);
                } else {
                    String error = String.format("Found target for '%s', but endpoint is invalid. Redundany error? Requested endpoint: %s", hypertyName, targetPaths[0]);
                    LOG.warn(error);
                    return error;
                }
            } else {
                return String.format("Could not find hyperty '%s'", hypertyName);
            }
        }

    }

    private HashMap<String, String> hypertyToInstanceMap = new HashMap<>();
    private HashMap<String, String> protocolstubToInstanceMap = new HashMap<>();
    private HashMap<Client, String> clientToHypertyMap = new HashMap<>();
    private HashMap<Client, String> clientToProtocolstubMap = new HashMap<>();

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
            boolean foundProtocolstubLink = false;
            LOG.info("checking object links of client: " + client);
            for (LinkObject link : client.getObjectLinks()) {
                String linkUrl = link.getUrl();
                LOG.debug("checking link: " + link.getUrl());
                if (!foundHypertyLink && linkUrl.startsWith("/" + HYPERTY_OBJ_ID + "/")) {
                    LOG.info("found hyperty link: " + linkUrl + "; skipping additional links");
                    foundHypertyLink = true;
                } else if (!foundProtocolstubLink && linkUrl.startsWith("/" + PROTOCOLSTUB_OBJ_ID + "/")) {
                    LOG.info("found found protocolstub link: " + linkUrl + "; skipping additional links");
                    foundProtocolstubLink = true;
                }
                // if both found, no need to keep checking
                if (foundHypertyLink && foundProtocolstubLink)
                    break;
            }

            if (foundHypertyLink) {
                ReadRequest request = new ReadRequest(HYPERTY_OBJ_ID);
                ValueResponse response = server.send(client, request);

                if (response.getCode() == ResponseCode.CONTENT) {
                    response.getContent().accept(new LwM2mNodeVisitor() {
                        @Override
                        public void visit(LwM2mObject object) {
                            Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                            int instanceID, resourceID;
                            for (LwM2mObjectInstance instance : instances.values()) {
                                instanceID = instance.getId();
                                LOG.debug("checking resources of instance " + instanceID);
                                Map<Integer, LwM2mResource> resources = instance.getResources();
                                for (LwM2mResource resource : resources.values()) {
                                    resourceID = resource.getId();
                                    LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                    // TODO: get value of correct field ('name' for now)
                                    // TODO: mapping: {<value> : /endpoint/1337/<instanceID>}
                                    if (resourceID == 1) { // current resource is name field
                                        String hypertyName = resource.getValue().value.toString();
                                        hypertyToInstanceMap.put(hypertyName, "/" + client.getEndpoint() + "/" + HYPERTY_OBJ_ID + "/" + instanceID);
                                        LOG.info("Added to client map -> " + hypertyName + ": " + hypertyToInstanceMap.get(hypertyName));
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
            } else if (foundProtocolstubLink) {
                ReadRequest request = new ReadRequest(PROTOCOLSTUB_OBJ_ID);
                ValueResponse response = server.send(client, request);

                if (response.getCode() == ResponseCode.CONTENT) {
                    response.getContent().accept(new LwM2mNodeVisitor() {
                        @Override
                        public void visit(LwM2mObject object) {
                            Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                            int instanceID, resourceID;
                            for (LwM2mObjectInstance instance : instances.values()) {
                                instanceID = instance.getId();
                                LOG.debug("checking resources of instance " + instanceID);
                                Map<Integer, LwM2mResource> resources = instance.getResources();
                                for (LwM2mResource resource : resources.values()) {
                                    resourceID = resource.getId();
                                    LOG.debug(String.format("#%d: %s", resourceID, resource.getValue().value));
                                    // TODO: get resourceID for field 'name' dynamically from model
                                    if (resourceID == 1) { // current resource is name field
                                        String protocolstubName = resource.getValue().value.toString();
                                        protocolstubToInstanceMap.put(protocolstubName, "/" + client.getEndpoint() + "/" + PROTOCOLSTUB_OBJ_ID + "/" + instanceID);
                                        LOG.info("Added to client map -> " + protocolstubName + ": " + protocolstubToInstanceMap.get(protocolstubName));
                                        // also map protocolstub name to client, for easy removal in case of client disconnect
                                        clientToProtocolstubMap.put(client, protocolstubName);
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
                    LOG.warn("Client contained protocolstub links on register, but requesting them failed with: " + gson.toJson(response));
                }
            } else {
                LOG.info("Client does not contain hyperties or protocolstubs");
            }
        }

        private void removeClient(Client client) {
            // check maps
            String hypertyName = clientToHypertyMap.remove(client);
            if (hypertyName != null) {
                LOG.debug("client contained hyperties, removing them from maps");
                String retVal = hypertyToInstanceMap.remove(hypertyName);
                if (retVal == null)
                    LOG.warn("unable to remove hyperty " + hypertyName + "from hypertyToInstanceMap!") ;
            }

            String protocolstubName = clientToProtocolstubMap.remove(client);
            if (protocolstubName != null) {
                LOG.debug("client contained protocolstubs, removing them from maps");
                protocolstubToInstanceMap.remove(protocolstubName);
            }
        }
    };


}
