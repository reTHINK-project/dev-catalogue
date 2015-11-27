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
package eu.rethink.catalogue.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.Value;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import org.eclipse.leshan.core.response.ValueResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A reference implementation for a reTHINK Catalogue Database
 */
public class CatalogueDatabase {
    private String registrationID;
    private static final Logger LOG = LoggerFactory.getLogger(CatalogueDatabase.class);

    private final int HYPERTY_MODEL_ID          = 1337;
    private final int PROTOSTUB_MODEL_ID        = 1338;

    public static void main(final String[] args) {
        if (args.length != 4 && args.length != 2) {
            System.out
                    .println("Usage:\njava -jar target/catalogue_database-*-jar-with-dependencies.jar ServerIP ServerPort [ClientIP] [ClientPort] ");
        } else {
            if (args.length == 4)
                new CatalogueDatabase(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
            else
                new CatalogueDatabase(args[0], Integer.parseInt(args[1]), "0", 0);
        }
    }

    public CatalogueDatabase(final String serverHostName, final int serverPort,
                             final String localHostName, final int localPort) {

        // parse files
        rethinkInstance[] parsedHyperties = parseHyperties();
        rethinkInstance[] parsedProtostubs = parseProtostubs();

        // get default models
        List<ObjectModel> objectModels = ObjectLoader.loadDefault();

        // add custom models from model.json
        InputStream modelStream = getClass().getResourceAsStream("/model.json");
        objectModels.addAll(ObjectLoader.loadJsonStream(modelStream));

        HashMap<Integer, ObjectModel> map = new HashMap<>();
        for (ObjectModel objectModel : objectModels) {
            map.put(objectModel.id, objectModel);
        }

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(map));
        initializer.setClassForObject(3, Device.class);
        initializer.setInstancesForObject(HYPERTY_MODEL_ID, parsedHyperties);
        initializer.setInstancesForObject(PROTOSTUB_MODEL_ID, parsedProtostubs);

        ObjectEnabler hypertyEnabler        = initializer.create(HYPERTY_MODEL_ID);
        ObjectEnabler protostubEnabler      = initializer.create(PROTOSTUB_MODEL_ID);

        List<ObjectEnabler> enablers = initializer.createMandatory();
        enablers.add(hypertyEnabler);
        enablers.add(protostubEnabler);

        // create idNameMap for hypterties
        LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
        Map<Integer, ResourceModel> model = hypertyEnabler.getObjectModel().resources;

        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
            idNameMap.put(entry.getKey(), entry.getValue().name);
        }

        // set id:name map on all hyperties
        for (rethinkInstance parsedHyperty : parsedHyperties) {
            parsedHyperty.setIdNameMap(idNameMap);
        }



        // create idNameMap for protostubs
        idNameMap = new LinkedHashMap<>();
        model = protostubEnabler.getObjectModel().resources;

        // populate id:name map from resources
        for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
            idNameMap.put(entry.getKey(), entry.getValue().name);
        }

        // set id:name map on all protostubs
        for (rethinkInstance parsedProtostub : parsedProtostubs) {
            parsedProtostub.setIdNameMap(idNameMap);
        }


        // Create client
        final InetSocketAddress clientAddress = new InetSocketAddress(localHostName, localPort);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClient client = new LeshanClient(clientAddress, serverAddress, new ArrayList<LwM2mObjectEnabler>(
                enablers));

        // Start the client
        client.start();

        // Register to the server
        final String endpointIdentifier = UUID.randomUUID().toString();
        RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));
        if (response == null) {
            LOG.warn("Registration request timeout");
            return;
        }

        LOG.debug("Device Registration (Success? " + response.getCode() + ")");
        if (response.getCode() != ResponseCode.CREATED) {
            System.err.println("If you're having issues connecting to the LWM2M endpoint, try using the DTLS port instead");
            return;
        }

        registrationID = response.getRegistrationID();

        LOG.info("Device: Registered Client Catalogue '" + registrationID + "'");

        // Deregister on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (registrationID != null) {
                    LOG.info("Device: Deregistering Client '" + registrationID + "'");
                    client.send(new DeregisterRequest(registrationID), 1000);
                    client.stop();
                }
            }
        });


    }

    private rethinkInstance[] parseHyperties() {
        // 1. open file, 2. parse hyperties.json
        Gson gson = new Gson();
        InputStream in = getClass().getResourceAsStream("/hyperties.json");
        String fileString = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        JsonArray jsonArray = new JsonParser().parse(fileString).getAsJsonArray();

        rethinkInstance[] parsedHyperties = new rethinkInstance[jsonArray.size()];
        int i = 0;
        for (JsonElement obj : jsonArray) {
            // make name:value map from json
            LinkedHashMap<String, String> nameValueMap = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive())
                    nameValueMap.put(entry.getKey(), entry.getValue().getAsString());
                else // stringify sourcePackage
                    nameValueMap.put(entry.getKey(), entry.getValue().toString());
            }

            LOG.debug("name:value map for hyperty #" + i + ": " + nameValueMap);

            parsedHyperties[i++] = new rethinkInstance(nameValueMap);


        }

        LOG.debug("parsed " + i + " hyperties.");
        return parsedHyperties;
    }

    private rethinkInstance[] parseProtostubs() {
        // 1. open file, 2. parse protostubs.json
        Gson gson = new Gson();
        InputStream in = getClass().getResourceAsStream("/protostubs.json");
        String fileString = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        JsonArray jsonArray = new JsonParser().parse(fileString).getAsJsonArray();

        rethinkInstance[] parsedProtostubs = new rethinkInstance[jsonArray.size()];
        int i = 0;
        for (JsonElement obj : jsonArray) {
            // make name:value map from json
            LinkedHashMap<String, String> nameValueMap = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive())
                    nameValueMap.put(entry.getKey(), entry.getValue().getAsString());
                else // stringify sourcePackage
                    nameValueMap.put(entry.getKey(), entry.getValue().toString());
            }

            LOG.debug("name:value map for protostub #" + i + ": " + nameValueMap);

            parsedProtostubs[i++] = new rethinkInstance(nameValueMap);

        }

        LOG.debug("parsed " + i + " protostubs.");
        return parsedProtostubs;
    }

    /**
     * InstanceEnabler for reTHINK resources.
     */
    public static class rethinkInstance extends BaseInstanceEnabler {

        /**
         * Set id:name map so instance knows which id corresponds to the correct field. (based on model.json)
         * @param idNameMap
         */
        public void setIdNameMap(Map<Integer, String> idNameMap) {
            this.idNameMap = idNameMap;
        }

        private Map<Integer, String> idNameMap = null;
        private final Map<String, String> nameValueMap;

        public rethinkInstance() {
            idNameMap = null;
            nameValueMap = null;
        }

        public rethinkInstance(Map<Integer, String> idNameMap, Map<String, String> NameValueMap) {
            this.idNameMap = idNameMap;
            this.nameValueMap = NameValueMap;
        }

        /**
         * Create a reTHINK instance with name:value mapping from model.json
         * @param nameValueMap
         */
        public rethinkInstance(Map<String, String> nameValueMap) {
            this.nameValueMap = nameValueMap;
        }

        @Override
        public ValueResponse read(int resourceid) {
            LOG.debug("Read on Catalogue Resource " + resourceid);
            String resourceName = idNameMap.get(resourceid);
            String resourceValue = nameValueMap.get(resourceName);
            // TODO: get correct value type from resource description

            LOG.debug("idNameMap returns: " + resourceName);
            LOG.debug("nameValueMap returns: " + resourceValue);


            if (resourceValue != null) {
                LOG.debug("returning: " + resourceValue);
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid, Value.newStringValue(resourceValue)));

            } else
                return super.read(resourceid);
        }

    }

    /**
     * Provides the default device description of a Database instance.
     */
    public static class Device extends BaseInstanceEnabler {

        public Device() {
            // notify new date each 5 second
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    fireResourceChange(13);
                }
            }, 5000, 5000);
        }

        @Override
        public ValueResponse read(int resourceid) {
            LOG.debug("Read on Device Resource " + resourceid);
            switch (resourceid) {
                case 0:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getManufacturer())));
                case 1:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getModelNumber())));
                case 2:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getSerialNumber())));
                case 3:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getFirmwareVersion())));
                case 9:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newIntegerValue(getBatteryLevel())));
                case 10:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newIntegerValue(getMemoryFree())));
                case 11:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            new Value<?>[]{Value.newIntegerValue(getErrorCode())}));
                case 13:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newDateValue(getCurrentTime())));
                case 14:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getUtcOffset())));
                case 15:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getTimezone())));
                case 16:
                    return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                            Value.newStringValue(getSupportedBinding())));
                default:
                    return super.read(resourceid);
            }
        }

        @Override
        public LwM2mResponse execute(int resourceid, byte[] params) {
            LOG.debug("Execute on Device resource " + resourceid);
            if (params != null && params.length != 0)
                LOG.debug("\t params " + new String(params));
            return new LwM2mResponse(ResponseCode.CHANGED);
        }

        @Override
        public LwM2mResponse write(int resourceid, LwM2mResource value) {
            LOG.debug("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
                case 13:
                    return new LwM2mResponse(ResponseCode.NOT_FOUND);
                case 14:
                    setUtcOffset((String) value.getValue().value);
                    fireResourceChange(resourceid);
                    return new LwM2mResponse(ResponseCode.CHANGED);
                case 15:
                    setTimezone((String) value.getValue().value);
                    fireResourceChange(resourceid);
                    return new LwM2mResponse(ResponseCode.CHANGED);
                default:
                    return super.write(resourceid, value);
            }
        }

        private String getManufacturer() {
            return "Rethink Example Catalogue";
        }

        private String getModelNumber() {
            return "Model 1337";
        }

        private String getSerialNumber() {
            return "RT-500-000-0001";
        }

        private String getFirmwareVersion() {
            return "0.1.0";
        }

        private int getErrorCode() {
            return 0;
        }

        private int getBatteryLevel() {
            final Random rand = new Random();
            return rand.nextInt(100);
        }

        private int getMemoryFree() {
            final Random rand = new Random();
            return rand.nextInt(50) + 114;
        }

        private Date getCurrentTime() {
            return new Date();
        }

        private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());

        private String getUtcOffset() {
            return utcOffset;
        }

        private void setUtcOffset(String t) {
            utcOffset = t;
        }

        private String timeZone = TimeZone.getDefault().getID();

        private String getTimezone() {
            return timeZone;
        }

        private void setTimezone(String t) {
            timeZone = t;
        }

        private String getSupportedBinding() {
            return "U";
        }
    }

}