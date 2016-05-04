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

import com.google.gson.*;
import org.apache.commons.lang.StringUtils;
import org.eclipse.leshan.LinkObject;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mNodeVisitor;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.eclipse.leshan.server.client.ClientUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * reTHINK specific Request Handler for requests on /.well-known/*
 */
public class RequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);

    // model IDs that define the custom models inside model.json
    private static final int HYPERTY_MODEL_ID = 1337;
    private static final int PROTOSTUB_MODEL_ID = 1338;
    private static final int RUNTIME_MODEL_ID = 1339;
    private static final int SCHEMA_MODEL_ID = 1340;
    private static final int IDPPROXY_MODEL_ID = 1341;
    private static final int SOURCEPACKAGE_MODEL_ID = 1350;

    // set of model IDs
    private static final Set<Integer> MODEL_IDS = new LinkedHashSet<>(Arrays.asList(HYPERTY_MODEL_ID, PROTOSTUB_MODEL_ID, RUNTIME_MODEL_ID, SCHEMA_MODEL_ID, IDPPROXY_MODEL_ID, SOURCEPACKAGE_MODEL_ID));

    private static final String WELLKNOWN_PREFIX = "/.well-known/";

    // types as defined in catalogue object folders and url paths
    private static final String HYPERTY_TYPE_NAME = "hyperty";
    private static final String PROTOSTUB_TYPE_NAME = "protocolstub";
    private static final String RUNTIME_TYPE_NAME = "runtime";
    private static final String SCHEMA_TYPE_NAME = "dataschema";
    private static final String IDPPROXY_TYPE_NAME = "idp-proxy";

    private static final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";

    // name to ID map
    private static Map<String, Integer> MODEL_NAME_TO_ID_MAP = new HashMap<>();

    // {MODEL_ID : { INSTANCE_NAME : INSTANCE_PATH}}
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

    // fields that define the name of the catalogue object when requesting it using the http interface
    private static final String NAME_FIELD_NAME = "objectName"; // for all objects except sourcePackage
    private static final String CGUID_FIELD_NAME = "cguid"; // for sourcePackages

    // {MODEL_ID : MODEL}
    private static final Map<Integer, Map<Integer, ResourceModel>> MODEL_MAP = new HashMap<>();

    // {MODEL_ID : {RESOURCE_NAME : RESOURCE_ID}}
    private static Map<Integer, Map<String, Integer>> resourceNameToIdMapMap = new ConcurrentHashMap<>();

    // {CLIENT_REG_ID : {MODEL_ID : [INSTANCE_NAME]}
    private static Map<String, Map<Integer, List<String>>> clientToObjectsMap = new ConcurrentHashMap<>();

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .create();

    private LeshanServer server;

    public RequestHandler(LeshanServer server) {
        this.server = server;

        // get LwM2mModel from model provider
        LwM2mModel customModel = server.getModelProvider().getObjectModel(null);

        // setup MODEL_MAP and resourceNameToIdMapMap (which is basically the inverse of it)
        for (Integer modelId : MODEL_IDS) {
            Map<Integer, ResourceModel> model = customModel.getObjectModel(modelId).resources;
            MODEL_MAP.put(modelId, model);
            Map<String, Integer> resourceNameToIdMap = new LinkedHashMap<>(model.size());
            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                resourceNameToIdMap.put(entry.getValue().name, entry.getKey());
            }

            resourceNameToIdMapMap.put(modelId, resourceNameToIdMap);
            LOG.debug("generated name:id map for model " + modelId + ": " + gson.toJson(resourceNameToIdMap));
        }

        server.getClientRegistry().addListener(clientRegistryListener);
    }

    /**
     * Makes a ReadRequest via CoAP to a client based on the provided URL path
     *
     * @param path - URL GET request path, e.g. /.well-known/hyperty/myHyperty/version
     * @return CoAP ReadResponse that the client returned, or an error message as ReadResponse
     */
    public RequestResponse handleGET(String path) {
        LOG.info("Handling GET for: " + path);
        // path should start with /.well-known/
        // but coap has no slash at the start, so check for it and prepend it if necessary.
        if (!path.startsWith("/"))
            path = "/" + path;

        // remove /.well-known/ from path
        path = StringUtils.removeStart(path, WELLKNOWN_PREFIX);
        // LOG.debug("adapted path: " + path);
        // split path up
        String[] pathParts = StringUtils.split(path, '/');

        // example path: <objectType>/<instanceName>/<resourceName>

        if (pathParts.length == 0) {
            String response = String.format("Please provide at least a type from %s or 'restart' or 'client'", MODEL_NAME_TO_ID_MAP.keySet());
            LOG.warn(response);
            return new RequestResponse(ReadResponse.internalServerError(response));
        } else if (pathParts.length == 1) { // hyperty | protostub | sourcepackage etc. only

            String type = pathParts[0];

            Integer id = MODEL_NAME_TO_ID_MAP.get(type);

            if (id != null) {
                String response = gson.toJson(nameToInstanceMapMap.get(id).keySet());
                LOG.info("Returning list: " + response);
                return new RequestResponse(ReadResponse.success(0, response), id);
            } else if (type.equals("restart")) {
                try {
                    restartClients();
                    String response = "Restart executed on all connected clients";
                    LOG.info(response);
                    return new RequestResponse(ReadResponse.success(0, response));
                } catch (InterruptedException e) {
                    LOG.warn("Restarting clients failed", e);
                    return new RequestResponse(ReadResponse.internalServerError("Restarting clients failed: " + e.getMessage()));
                }
            } else if (type.equals("client")) {
                List<String> clients = new LinkedList<>();
                for (Client client : server.getClientRegistry().allClients()) {
                    clients.add(client.getEndpoint() + " (" + client.getRegistrationId() + ")");
                }
                String response = gson.toJson(clients);
                LOG.info("Returinging client list: " + response);
                return new RequestResponse(ReadResponse.success(0, response));
            } else {
                String response = String.format("Unknown object type, please use one of: %s or 'restart' or 'client'", MODEL_NAME_TO_ID_MAP.keySet());
                LOG.warn(response);
                return new RequestResponse(ReadResponse.internalServerError(response), -1);
            }

        } else {
            String modelType = pathParts[0];
            String instanceName = pathParts[1];
            String resourceName = null;
            String[] details = null;

            LOG.debug("modelType:    " + modelType);
            LOG.debug("instanceName: " + instanceName);

            if (pathParts.length > 2) {
                resourceName = pathParts[2];
                LOG.debug("resourceName: " + resourceName);
            }

            if (pathParts.length > 3) {
                details = Arrays.copyOfRange(pathParts, 3, pathParts.length);
                LOG.debug("further specifications: " + Arrays.toString(details));
            }

            //check if resourceName is valid
            Integer id = MODEL_NAME_TO_ID_MAP.get(modelType);
            Integer resourceID;
            String target;

            if (id != null) {
                Map<String, Integer> resourceNameToIdMap = resourceNameToIdMapMap.get(id);
                resourceID = resourceNameToIdMap.get(resourceName);

                //resource name was given, but not found in the name:id map
                if (resourceName != null && resourceID == null) {
                    String response = String.format("invalid resource name '%s'. Please use one of the following: %s", resourceName, resourceNameToIdMap.keySet());
                    LOG.warn(response);
                    return new RequestResponse(ReadResponse.internalServerError(response), id);

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
                        String t = StringUtils.removeStart(target, "/" + targetPaths[0]);
                        LOG.debug("requesting {}", t);
                        ReadRequest request = new ReadRequest(t);
                        try {
                            long startTime = System.currentTimeMillis();
                            RequestResponse response = new RequestResponse(server.send(client, request), id);
                            long respTime = System.currentTimeMillis();
                            LOG.debug("response received after {}ms", respTime - startTime);
                            if (details != null && response.isSuccess()) {
                                try {
                                    // assume json objects down the "detail" route
                                    JsonElement current = response.getJson();
                                    for (String detail : details) {
                                        //LOG.debug("current: {}", current);
                                        current = current.getAsJsonObject().get(detail);
                                    }
                                    //LOG.debug("final current: {}");
                                    // now current is what we want
                                    String sResp = current.toString();
                                    LOG.info("Returning: " + sResp);
                                    return new RequestResponse(ReadResponse.success(0, sResp));
                                } catch (Exception e) {
                                    //e.printStackTrace();
                                    String detailPath = "";
                                    for (String detail : details) {
                                        detailPath += "/" + detail;
                                    }
                                    String error = detailPath + " not found in " + resourceName;
                                    LOG.warn(error, e);
                                    return new RequestResponse(new ReadResponse(ResponseCode.NOT_FOUND, null, error));
                                }
                            } else {
                                return response;
                            }
                        } catch (InterruptedException e) {
                            String error = "unable to request " + t + " from client " + client;
                            LOG.warn(error, e);
                            return new RequestResponse(ReadResponse.internalServerError(error + ": " + e.getMessage()), id);
                        }
                    } else {
                        String response = String.format("Found target for '%s', but endpoint is invalid. Redundany error? Requested endpoint: %s", instanceName, targetPaths[0]);
                        LOG.warn(response);
                        return new RequestResponse(ReadResponse.internalServerError(response), id);
                    }
                } else {
                    String response = String.format("Could not find instance: %s", instanceName);
                    LOG.warn(response);
                    return new RequestResponse(ReadResponse.internalServerError(response), id);

                }
            } else if (pathParts[0].equals("restart")) {
                LOG.info("trying to disconnect {}", instanceName);
                Client client = server.getClientRegistry().get(instanceName);
                LOG.debug("search for client {} returned {}", instanceName, client);
                if (client != null) {
                    try {
                        ExecuteResponse executeResponse = restartClient(client);
                        LOG.info("Restarting client {} {}", client.getEndpoint(), executeResponse.isSuccess() ? "succeeded" : "failed");
                        return new RequestResponse(executeResponse);
                    } catch (InterruptedException e) {
                        String errorMessage = "Unable to restart client " + client.getEndpoint() + ": " + e.getMessage();
                        LOG.warn(errorMessage, e);
                        return new RequestResponse(ReadResponse.internalServerError(errorMessage));
                    }
                } else {
                    String errorMessage = "Client " + instanceName + " not found";
                    LOG.warn(errorMessage);
                    return new RequestResponse(ReadResponse.internalServerError(errorMessage));
                }
            } else if (pathParts[0].equals("client")) {
                LOG.info("trying to get information about client {}", instanceName);
                Client client = server.getClientRegistry().get(instanceName);
                if (client != null) {
                    String response = client.toString();
                    LOG.info("client " + instanceName + " found: {}", response);
                    return new RequestResponse(ReadResponse.success(0, response));
                } else {
                    String errorMessage = "client " + instanceName + " not found";
                    LOG.warn(errorMessage);
                    return new RequestResponse(new ReadResponse(ResponseCode.NOT_FOUND, null, errorMessage));
                }
            } else {
                String response = String.format("Unknown object type, please use one of: %s or 'restart'", MODEL_NAME_TO_ID_MAP.keySet());
                LOG.warn(response);
                return new RequestResponse(ReadResponse.internalServerError(response));
            }
        }
    }

    /**
     * Keeps track of currently registered clients.
     */
    private ClientRegistryListener clientRegistryListener = new ClientRegistryListener() {
        @Override
        public void registered(final Client client) {
            LOG.info("'{}' registered", client.getEndpoint());
            try {
                checkClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while checking the client", e);
                //e.printStackTrace();
            }

        }

        @Override
        public void updated(ClientUpdate update, Client clientUpdated) {
            LOG.debug("'{}' updated", clientUpdated.getEndpoint());

            //try {
            //    removeClient(clientUpdated);
            //} catch (Exception e) {
            //    LOG.error("Something went wrong while removing the client", e);
            //    //e.printStackTrace();
            //}
            //
            //try {
            //    checkClient(clientUpdated);
            //} catch (Exception e) {
            //    LOG.error("Something went wrong while checking the client", e);
            //    //e.printStackTrace();
            //}
        }

        @Override
        public void unregistered(Client client) {
            LOG.info("'{}' unregistered", client.getEndpoint());

            try {
                removeClient(client);
            } catch (Exception e) {
                LOG.error("Something went wrong while removing the client", e);
                e.printStackTrace();
            }
        }

        /**
         * Checks if client has one of our custom resources.
         */
        private void checkClient(final Client client) {

            final List<String> foundModels = new LinkedList<>();

            //LOG.debug("checking object links of client: " + client);
            for (LinkObject link : client.getObjectLinks()) {
                String linkUrl = link.getUrl();
                //LOG.debug("checking link: " + link.getUrl());
                int i = linkUrl.indexOf("/", 1); //only supported if this returns an index
                if (i > -1) {
                    int id = Integer.parseInt(linkUrl.substring(1, i));
                    if (MODEL_IDS.contains(id))
                        foundModels.add(link.getUrl());
                }
            }
            //LOG.debug("{} contains: {}", client.getEndpoint(), foundModels);

            //request instances of each found model
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    final Map<String, String> generatedMapping = new LinkedHashMap<>();
                    for (final String foundModelLink : foundModels) {
                        //LOG.debug("send readRequest for modelLink: {}", foundModelLink);

                        final int model;
                        if (foundModelLink.indexOf("/", 1) > -1) {
                            model = Integer.parseInt(foundModelLink.substring(1, foundModelLink.indexOf("/", 1)));
                        } else {
                            LOG.error("unable to get model ID from modelLink '{}", foundModelLink);
                            return;
                        }

                        Integer identifier = resourceNameToIdMapMap.get(model).get(NAME_FIELD_NAME);
                        if (identifier == null)
                            identifier = resourceNameToIdMapMap.get(model).get(CGUID_FIELD_NAME);

                        String target = foundModelLink + "/" + identifier;
                        ReadRequest request = new ReadRequest(target);
                        ReadResponse response;
                        try {
                            response = server.send(client, request);
                        } catch (InterruptedException e) {
                            LOG.error("unable request " + target + " from '" + client.getEndpoint() + "'", e);
                            return;
                        }

                        if (response.getCode() == ResponseCode.CONTENT) {
                            response.getContent().accept(new LwM2mNodeVisitor() {
                                @Override
                                public void visit(LwM2mObject object) {
                                    LOG.warn("object visit: " + object + " (this should not happen)");
                                }

                                @Override
                                public void visit(LwM2mObjectInstance instance) {
                                    LOG.warn("instance visit: " + instance + " (this should not happen)");
                                }

                                @Override
                                public void visit(LwM2mResource resource) {
                                    LOG.debug("resource visit: " + resource);
                                    String objectName = resource.getValue().toString();
                                    Map<String, String> nametToInstanceMap = nameToInstanceMapMap.get(model);
                                    String instanceVal = "/" + client.getEndpoint() + foundModelLink;
                                    nametToInstanceMap.put(objectName, instanceVal);

                                    //map object names to client, for easy removal in case of client disconnect
                                    Map<Integer, List<String>> modelObjectsMap = clientToObjectsMap.get(client.getRegistrationId());
                                    if (modelObjectsMap == null) {
                                        modelObjectsMap = new ConcurrentHashMap<>();
                                        clientToObjectsMap.put(client.getRegistrationId(), modelObjectsMap);
                                    }

                                    List<String> objectNames = modelObjectsMap.get(model);
                                    if (objectNames == null) {
                                        objectNames = new CopyOnWriteArrayList<>();
                                        modelObjectsMap.put(model, objectNames);
                                    }

                                    objectNames.add(objectName);

                                    generatedMapping.put(foundModelLink, objectName);

                                    LOG.debug("Added to client map -> " + objectName + ": " + nametToInstanceMap.get(objectName));
                                }
                            });
                        } else {
                            LOG.warn("'{}' contained object links on register, but requesting them failed with: " + gson.toJson(response), client.getEndpoint());
                        }
                    }
                    LOG.info("'{}' contains: {}", client.getEndpoint(), gson.toJson(generatedMapping));
                }
            });
            t.start();
        }

        /**
         * Tries to remove client and its resources from maps.
         */
        private void removeClient(Client client) {
            Map<Integer, List<String>> modelObjectsMap = clientToObjectsMap.get(client.getRegistrationId());
            if (modelObjectsMap != null) {
                for (Map.Entry<Integer, List<String>> entry : modelObjectsMap.entrySet()) {
                    Map<String, String> nameToInstanceMap = nameToInstanceMapMap.get(entry.getKey());
                    for (String objectName : entry.getValue()) {
                        nameToInstanceMap.remove(objectName);
                    }
                }
            }
            clientToObjectsMap.remove(client.getRegistrationId());
        }
    };

    /**
     * Tries to restart all connected clients.
     *
     * @throws InterruptedException
     */
    public void restartClients() throws InterruptedException {
        LOG.info("Restarting all clients...");
        for (Client client : server.getClientRegistry().allClients()) {
            restartClient(client);
        }
    }

    /**
     * Tries to restart a connected client.
     *
     * @param client - Client that needs to be restarted
     * @return ExecuteResponse from client
     * @throws InterruptedException
     */
    public ExecuteResponse restartClient(Client client) throws InterruptedException {
        //LOG.info("Trying to restart {}", client.getEndpoint());
        ExecuteResponse response = server.send(client, new ExecuteRequest(3, 0, 4));
        LOG.info("Restarting client {} " + (response.isSuccess() ? "succeeded" : ("failed: " + response.getCode())), client.getEndpoint());
        return response;
    }

    public class RequestResponse {
        private LwM2mResponse response;
        private int model;


        public RequestResponse(LwM2mResponse response) {
            this.response = response;
            this.model = -1;
        }

        public RequestResponse(ReadResponse response, int model) {
            this.response = response;
            this.model = model;
        }

        public boolean isSuccess() {
            return response.isSuccess();
        }

        public boolean isFailure() {
            return response.isFailure();
        }

        public ResponseCode getCode() {
            return response.getCode();
        }

        public JsonElement getJson() {
            final JsonElement[] resp = new JsonElement[1];

            if (response.isFailure()) {
                if (response.getErrorMessage() != null)
                    return new JsonPrimitive(response.getErrorMessage());
                else
                    return new JsonPrimitive("");
            }

            final Map<Integer, ResourceModel> modelMap = MODEL_MAP.get(model);

            if (response instanceof ReadResponse) {
                ((ReadResponse) response).getContent().accept(new LwM2mNodeVisitor() {
                    @Override
                    public void visit(LwM2mObject object) {
                        LOG.warn("visiting object {} (unintended behaviour!)", object);
                        resp[0] = gson.toJsonTree(object);
                    }

                    @Override
                    public void visit(LwM2mObjectInstance instance) {
                        LOG.debug("visiting instance {}", instance);
                        JsonObject jResponse = new JsonObject();
                        Map<Integer, LwM2mResource> resources = instance.getResources();
                        // parse resources into json
                        for (Map.Entry<Integer, LwM2mResource> entry : resources.entrySet()) {
                            String name = modelMap.get(entry.getKey()).name;
                            String value = entry.getValue().getValue().toString();
                            try {
                                jResponse.add(name, gson.fromJson(value, JsonElement.class));
                            } catch (JsonSyntaxException e) {
                                jResponse.addProperty(name, value);
                            }
                        }
                        resp[0] = jResponse;
                    }

                    @Override
                    public void visit(LwM2mResource resource) {
                        LOG.debug("visiting resource {}", resource);
                        String val = resource.getValue().toString();
                        //LOG.debug("value: {}", val);

                        try {
                            resp[0] = gson.fromJson(val, JsonElement.class);
                        } catch (JsonSyntaxException e) {
                            resp[0] = new JsonPrimitive(val);
                        }
                    }
                });
            } else if (response instanceof ExecuteResponse) {
                LOG.debug("is executeResponse");
                resp[0] = new JsonPrimitive("Successfully executed command");
            }
            //LOG.debug("returning json: {}", resp[0]);
            return resp[0];
        }

        public String getJsonString() {
            JsonElement json = getJson();
            //LOG.debug("json: {}", json);
            if (json.isJsonPrimitive()) {
                return json.getAsJsonPrimitive().getAsString();
            }
            return gson.toJson(json);
        }

        @Override
        public String toString() {
            return "RequestResponse{" +
                    "response=" + response +
                    ", model=" + model +
                    '}';
        }
    }
}