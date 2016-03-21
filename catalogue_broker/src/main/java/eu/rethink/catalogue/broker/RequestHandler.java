/**
 * Copyright [2015-2017] Fraunhofer Gesellschaft e.V., Institute for
 * Open Communication Systems (FOKUS)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package eu.rethink.catalogue.broker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang.StringUtils;
import org.eclipse.leshan.LinkObject;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.*;
import org.eclipse.leshan.core.request.ReadRequest;
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
    //model IDs that define the custom models inside model.json
    private static final int HYPERTY_MODEL_ID = 1337;
    private static final int PROTOSTUB_MODEL_ID = 1338;
    private static final int RUNTIME_MODEL_ID = 1339;
    private static final int SCHEMA_MODEL_ID = 1340;
    private static final int IDPPROXY_MODEL_ID = 1341;

    private static final int SOURCEPACKAGE_MODEL_ID = 1350;


    private static final Set<Integer> MODEL_IDS = new LinkedHashSet<>(Arrays.asList(HYPERTY_MODEL_ID, PROTOSTUB_MODEL_ID, RUNTIME_MODEL_ID, SCHEMA_MODEL_ID, IDPPROXY_MODEL_ID, SOURCEPACKAGE_MODEL_ID));

    private static final String WELLKNOWN_PREFIX = "/.well-known/";

    private static final String HYPERTY_TYPE_NAME = "hyperty";
    private static final String PROTOSTUB_TYPE_NAME = "protocolstub";
    private static final String RUNTIME_TYPE_NAME = "runtime";
    private static final String SCHEMA_TYPE_NAME = "dataschema";
    private static final String IDPPROXY_TYPE_NAME = "idp-proxy";

    private static final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";


    private static Map<String, Integer> MODEL_NAME_TO_ID_MAP = new HashMap<>();
    private static Map<Integer, Map<String, String>> nameToInstanceMapMap = new HashMap<>();

    static {
        MODEL_NAME_TO_ID_MAP.put(HYPERTY_TYPE_NAME, HYPERTY_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(PROTOSTUB_TYPE_NAME, PROTOSTUB_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(RUNTIME_TYPE_NAME, RUNTIME_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(SCHEMA_TYPE_NAME, SCHEMA_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(IDPPROXY_TYPE_NAME, IDPPROXY_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(SOURCEPACKAGE_TYPE_NAME, SOURCEPACKAGE_MODEL_ID);

        for (Integer modelId : MODEL_NAME_TO_ID_MAP.values()) {
            nameToInstanceMapMap.put(modelId, new HashMap<String, String>());
        }
    }

    private static final String NAME_FIELD_NAME = "objectName";

    private static final Map<Integer, Map<Integer, ResourceModel>> MODEL_MAP = new HashMap<>();

    private static Map<Integer, Map<String, Integer>> resourceNameToIdMapMap = new HashMap<>();
    private static Map<Client, Map<Integer, List<String>>> clientToObjectsMap = new HashMap<>();

    private LeshanServer server;
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .create();
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);

    public RequestHandler(LeshanServer server) {
        this.server = server;

        // get LwM2mModel from modelprovider
        LwM2mModel customModel = server.getModelProvider().getObjectModel(null);

        for (Integer modelId : MODEL_IDS) {
            ObjectEnabler modelEnabler = new ObjectsInitializer(customModel).create(modelId);
            Map<Integer, ResourceModel> model = modelEnabler.getObjectModel().resources;
            MODEL_MAP.put(modelId, model);
            Map<String, Integer> resourceNameToIdMap = new LinkedHashMap<>(model.size());
            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                resourceNameToIdMap.put(entry.getValue().name, entry.getKey());
            }

            resourceNameToIdMapMap.put(modelId, resourceNameToIdMap);
            LOG.debug("generated name:id map for model " + modelId + ":\r\n" + gson.toJson(resourceNameToIdMap));
        }

        server.getClientRegistry().addListener(clientRegistryListener);
    }

    public String handleGET(String path) {
        LOG.info("Handling GET for: " + path);
        //path should start with /.well-known/
        //but coap has no slash at the start, so check for it and prepend it if necessary.
        if (!path.startsWith("/"))
            path = "/" + path;

        //remove /.well-known/ from path
        path = StringUtils.removeStart(path, WELLKNOWN_PREFIX);
        LOG.debug("adapted path: " + path);
        //split path up
        String[] pathParts = StringUtils.split(path, '/');

        //example path: <endpoint>/<objectID>/<instance>/<resourceID>

        //TODO: no endpoint given -> return all clients or ask for more info?
        if (pathParts.length == 0) {
            String response = "Please provide resource type and (optional) name and (optional) resource name. Example: /hyperty/MyHyperty/sourceCode";
            ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
            return encodeErrorResponse(errorResp);
        } else if (pathParts.length == 1) { //hyperty | protostub | sourcepackage etc. only
            String type = pathParts[0];

            Integer id = MODEL_NAME_TO_ID_MAP.get(type);

            if (id != null) {
                return gson.toJson(nameToInstanceMapMap.get(id).keySet());
            } else {
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

            //check if resourceName is valid
            Integer id = MODEL_NAME_TO_ID_MAP.get(modelType);
            Integer resourceID = null;

            String target = null;

            if (id != null) {
                Map<String, Integer> resourceNameToIdMap = resourceNameToIdMapMap.get(id);
                resourceID = resourceNameToIdMap.get(resourceName);

                //resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, resourceNameToIdMap.keySet());
                    ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
                    return encodeErrorResponse(errorResp);
                }

                Map<String, String> nameToInstanceMap = nameToInstanceMapMap.get(id);

                target = nameToInstanceMap.get(instanceName);
                if (target == null && instanceName.equals("default")) {
                    LOG.debug("default object requested and no default defined -> returning first in map");
                    target = nameToInstanceMap.values().iterator().next();
                }
                LOG.debug(String.format("target for object '%s': %s", instanceName, target));

                if (target != null) {
                    if (resourceID != null)
                        target += "/" + resourceID;

                    String[] targetPaths = StringUtils.split(target, "/");
                    LOG.debug("checking endpoint: " + targetPaths[0]);
                    Client client = server.getClientRegistry().get(targetPaths[0]);
                    if (client != null) {
                        ReadRequest request = new ReadRequest(StringUtils.removeStart(target, "/" + targetPaths[0]));
                        ValueResponse response = server.send(client, request);
                        if (!response.getCode().equals(ResponseCode.CONTENT)) {
                            return encodeErrorResponse(createResponse(response.getCode(), "Unable to retrieve " + path));
                        } else {
                            return encodeResponse(response, modelType);

                        }
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
            } else {
                String response = String.format("invalid object type, please use one of: %s", MODEL_NAME_TO_ID_MAP.keySet());
                ValueResponse errorResp = createResponse(ResponseCode.BAD_REQUEST, response);
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
            LOG.info("Client registered:\r\n" + gson.toJson(client));
            try {
                checkClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while checking the client.", e);
                //e.printStackTrace();
            }

        }

        @Override
        public void updated(Client clientUpdated) {
            LOG.info("Client updated: " + clientUpdated);

            try {
                removeClient(clientUpdated);
            } catch (Exception e) {
                LOG.error("Something went wrong while removing the client.", e);
                //e.printStackTrace();
            }

            try {
                checkClient(clientUpdated);
            } catch (Exception e) {
                LOG.error("Something went wrong while checking the client.", e);
                //e.printStackTrace();
            }
        }

        @Override
        public void unregistered(Client client) {
            LOG.info("Client unregistered: " + client);

            try {
                removeClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while removing the client.", e);
                //e.printStackTrace();
            }
        }

        /**
         * Checks if client has one of our custom resources.
         */
        private void checkClient(final Client client) {

            Set<Integer> foundModels = new LinkedHashSet<>();

            //LOG.debug("checking object links of client: " + client);
            for (LinkObject link : client.getObjectLinks()) {
                if (foundModels.equals(MODEL_IDS)) {
                    //exit condition
                    //LOG.debug("all supported models found");
                    break;
                }
                String linkUrl = link.getUrl();
                LOG.debug("checking link: " + link.getUrl());
                int i = linkUrl.indexOf("/", 1); //only supported if this returns an index
                if (i > -1) {
                    int id = Integer.parseInt(linkUrl.substring(1, i));
                    if (MODEL_IDS.contains(id))
                        foundModels.add(id);
                }
            }

            //request instances of each found model
            for (final Integer foundModelId : foundModels) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ReadRequest request = new ReadRequest(foundModelId);
                        ValueResponse response = server.send(client, request);

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    Map<Integer, LwM2mObjectInstance> instances = object.getInstances();
                                    int instanceID, resourceID;
                                    int idFieldID = resourceNameToIdMapMap.get(foundModelId).get(NAME_FIELD_NAME);
                                    LinkedList<String> newObjectNames = new LinkedList<>();
                                    for (LwM2mObjectInstance instance : instances.values()) {
                                        instanceID = instance.getId();
                                        //LOG.debug("checking resources of /" + foundModelId + "/" + instanceID);
                                        Map<Integer, LwM2mResource> resources = instance.getResources();
                                        for (LwM2mResource resource : resources.values()) {
                                            resourceID = resource.getId();
                                            //LOG.debug(String.format("/%s/%s/%s = %s", foundModelId, instanceID, resourceID, resource.getValue().value));
                                            if (resourceID == idFieldID) { //current resource is name field
                                                String objectName = resource.getValue().value.toString();
                                                Map<String, String> nametToInstanceMap = nameToInstanceMapMap.get(foundModelId);

                                                nametToInstanceMap.put(objectName, "/" + client.getEndpoint() + "/" + foundModelId + "/" + instanceID);

                                                newObjectNames.add(objectName);
                                                //LOG.debug("Added to client map -> " + objectName + ": " + nametToInstanceMap.get(objectName));
                                            }

                                        }
                                    }
                                    //map object names to client, for easy removal in case of client disconnect
                                    Map<Integer, List<String>> modelObjectsMap = clientToObjectsMap.get(client);
                                    if (modelObjectsMap == null) {
                                        modelObjectsMap = new HashMap<>();
                                    }

                                    List<String> objectNames = modelObjectsMap.get(foundModelId);
                                    if (objectNames == null) {
                                        objectNames = new LinkedList<>();
                                    }

                                    objectNames.addAll(newObjectNames);
                                    modelObjectsMap.put(foundModelId, objectNames);
                                    clientToObjectsMap.put(client, modelObjectsMap);

                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance + " (this should not happen)");
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.warn("resource visit: " + resource + " (this should not happen)");
                                }
                            });
                        } else {
                            LOG.warn("Client contained object links on register, but requesting them failed with: " + gson.toJson(response));
                        }
                    }
                });
                t.start();

                // FIXME this helps debugging, but is not necessary and costs time.
                //try {
                //    t.join();
                //} catch (InterruptedException e) {
                //    e.printStackTrace();
                //}
            }
        }

        /**
         * Tries to remove client and its resources from maps.
         */
        private void removeClient(Client client) {
            Map<Integer, List<String>> modelObjectsMap = clientToObjectsMap.get(client);

            if (modelObjectsMap != null) {
                for (Map.Entry<Integer, List<String>> entry : modelObjectsMap.entrySet()) {
                    Map<String, String> nameToInstanceMap = nameToInstanceMapMap.get(entry.getKey());
                    for (String objectName : entry.getValue()) {
                        nameToInstanceMap.remove(objectName);
                    }
                }
            }
        }
    };

    /**
     * Returns a ValueResponse based on the given code and content.
     * If no code is provided, ResponseCode.CONTENT will be used
     *
     * @param code    response code of the response
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
     *
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
        if (response.getCode().equals(ResponseCode.NOT_FOUND))
            return encodeResponse(response, modelType, true);
        else
            return encodeResponse(response, modelType, false);
    }


    /**
     * Parses a response to a json string.
     *
     * @param response ValueResponse to be encoded to json
     * @return response as json
     */
    private String encodeResponse(final ValueResponse response, final String modelType, final boolean isError) {
        LOG.debug("encoding response: " + response);
        LOG.debug("encoding response: " + gson.toJson(response));

        final Map<Integer, ResourceModel> model = MODEL_MAP.get(MODEL_NAME_TO_ID_MAP.get(modelType));

        final LinkedHashMap<String, String> instanceMap = new LinkedHashMap<>();

        //TODO: use proper exception type
        //LOG.debug("isError: " + isError + ", model: " + model);
        if (!isError && model == null) {
            throw new NullPointerException("could not resolve model type " + modelType);
        }

        final String[] result = new String[1];

        response.getContent().accept(new LwM2mNodeVisitor() {
            @Override
            public void visit(LwM2mObject object) {
                LOG.warn("visiting object: " + object + " (unintended behaviour!)");
            }

            @Override
            public void visit(LwM2mObjectInstance instance) {
                //LOG.debug("visiting instance: " + instance);
                Map<Integer, LwM2mResource> resources = instance.getResources();
                //LOG.debug("resources: " + resources);
                for (Map.Entry<Integer, LwM2mResource> entry : resources.entrySet()) {
                    instanceMap.put(model.get(entry.getKey()).name, (String) entry.getValue().getValue().value);
                }

                //LOG.debug("final instanceMap: " + instanceMap);

                result[0] = gson.toJson(instanceMap);
            }

            @Override
            public void visit(LwM2mResource resource) {
                //LOG.debug("visiting resource: " + resource);
                if (isError) {
                    instanceMap.put(response.getCode().name(), (String) resource.getValue().value);

                    HashMap<String, Map<String, String>> errorMap = new HashMap<>(1);
                    errorMap.put("ERROR", instanceMap);
                    result[0] = gson.toJson(errorMap);
                } else {
                    result[0] = (String) resource.getValue().value;
                }

            }
        });


        return result[0];
    }

}