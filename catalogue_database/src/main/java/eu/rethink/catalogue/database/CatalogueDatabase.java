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
package eu.rethink.catalogue.database;

import com.google.gson.*;
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

import java.io.*;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A reference implementation for a reTHINK Catalogue Database
 */
public class CatalogueDatabase {
    private String registrationID;
    private static final Logger LOG = LoggerFactory.getLogger(CatalogueDatabase.class);

    //model IDs that define the custom models inside model.json
    private static final int HYPERTY_MODEL_ID = 1337;
    private static final int PROTOSTUB_MODEL_ID = 1338;
    private static final int RUNTIME_MODEL_ID = 1339;
    private static final int SCHEMA_MODEL_ID = 1340;
    private static final int IDPPROXY_MODEL_ID = 1341;

    private static final int SOURCEPACKAGE_MODEL_ID = 1350;


    private static final Set<Integer> MODEL_IDS = new HashSet<>(Arrays.asList(HYPERTY_MODEL_ID, PROTOSTUB_MODEL_ID, RUNTIME_MODEL_ID, SCHEMA_MODEL_ID, IDPPROXY_MODEL_ID, SOURCEPACKAGE_MODEL_ID));

    private static final String HYPERTY_TYPE_NAME = "hyperty";
    private static final String PROTOSTUB_TYPE_NAME = "protocolstub";
    private static final String RUNTIME_TYPE_NAME = "runtime";
    private static final String SCHEMA_TYPE_NAME = "dataschema";
    private static final String IDPPROXY_TYPE_NAME = "idp-proxy";

    private static final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";

    private static Map<Integer, String> MODEL_ID_TO_NAME_MAP = new HashMap<>();

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    static {
        MODEL_ID_TO_NAME_MAP.put(HYPERTY_MODEL_ID, HYPERTY_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(PROTOSTUB_MODEL_ID, PROTOSTUB_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(RUNTIME_MODEL_ID, RUNTIME_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(SCHEMA_MODEL_ID, SCHEMA_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(IDPPROXY_MODEL_ID, IDPPROXY_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(SOURCEPACKAGE_MODEL_ID, SOURCEPACKAGE_TYPE_NAME);
    }

    private static final String NAME_FIELD_NAME = "objectName";

    private String accessURL;

    private final String DEFAULT_SERVER_HOSTNAME = "localhost";
    private final int DEFAULT_SERVER_COAP_PORT = 5683;

    public static void main(final String[] args) {
        String hostName = null, objPath = null;
        boolean useHttp = false;
        int port = -1;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "-host":
                    hostName = args[++i];
                    break;
                case "-usehttp":
                    if (args.length <= i + 1 || args[i + 1].startsWith("-")) { // check if boolean value is not given, assume true
                        useHttp = true;
                    } else {
                        useHttp = Boolean.parseBoolean(args[++i]);
                    }
                    break;
                case "-p":
                case "-port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-o":
                case "-op":
                case "-objPath":
                case "-objpath":
                    objPath = args[++i];
                    break;
            }
        }
        new CatalogueDatabase(hostName, port, objPath, useHttp);
    }

    public CatalogueDatabase(String serverHostName, int serverPort, String catObjsPath, boolean useHttp) {
        LOG.info("Starting Catalogue Database...");

        // check arguments
        if (serverHostName == null)
            serverHostName = DEFAULT_SERVER_HOSTNAME;

        if (serverPort == -1)
            serverPort = DEFAULT_SERVER_COAP_PORT;

        if (catObjsPath == null) {
            catObjsPath = "./catalogue_objects";
        }

        if (useHttp) {
            accessURL = "http://" + serverHostName + "/.well-known/";
        } else {
            accessURL = "https://" + serverHostName + "/.well-known/";
        }

        LOG.info("Catalogue Broker host name: " + serverHostName);
        LOG.info("Catalogue Broker CoAP port: " + serverPort);
        LOG.info("Catalogue Objects location: " + catObjsPath);

        Map<Integer, RethinkInstance[]> resultMap = parseFiles(catObjsPath);

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
        List<ObjectEnabler> enablers = initializer.createMandatory();

        for (Integer modelId : MODEL_IDS) {
            RethinkInstance[] instances = resultMap.get(modelId);

            if (instances != null) {
                initializer.setInstancesForObject(modelId, instances);
                ObjectEnabler enabler = initializer.create(modelId);
                enablers.add(enabler);

                // create idNameMap for hyperties
                LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
                Map<Integer, ResourceModel> model = enabler.getObjectModel().resources;

                // populate id:name map from resources
                for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                    idNameMap.put(entry.getKey(), entry.getValue().name);
                }

                // set id:name map on all hyperties
                for (RethinkInstance parsedHyperty : instances) {
                    parsedHyperty.setIdNameMap(idNameMap);
                }
            }
        }

        // Create client
        final InetSocketAddress clientAddress = new InetSocketAddress("0", 0);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClient client = new LeshanClient(clientAddress, serverAddress, new ArrayList<LwM2mObjectEnabler>(
                enablers));

        // Start the client
        client.start();

        // Register to the server
        LOG.info(String.format("Registering on %s...", serverAddress));
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

    JsonParser parser = new JsonParser();

    private RethinkInstance parseCatalogueObject(File dir) throws FileNotFoundException {
        File desc = new File(dir, "description.json");
        File pkg = new File(dir, "sourcePackage.json");

        // 1. parse hyperty
        RethinkInstance instance = createFromFile(desc);

        // 2. parse sourcePackage
        if (instance.nameValueMap.get("sourcePackage") == null && pkg.exists()) {
            RethinkInstance sourcePackage = createFromFile(pkg);

            // 3. attach code to sourcePackage
            if (sourcePackage.nameValueMap.get("sourceCode") == null) {
                // try to get sourceCode file
                File code = null;
                for (File file : dir.listFiles()) {
                    if (file.getName().startsWith("sourceCode")) {
                        code = file;
                        break;
                    }
                }
                if (code != null)
                    sourcePackage.setSourceCodeFile(code);
            }
            // 4. add sourcePackage to hyperty
            instance.setSourcePackage(sourcePackage);
        }
        return instance;
    }

    private Map<Integer, RethinkInstance[]> parseFiles(String sourcePath) {
        if (sourcePath == null)
            sourcePath = "./";

        HashMap<Integer, RethinkInstance[]> resultMap = new HashMap<>();
        Gson gson = new Gson();

        File catObjsFolder = new File(sourcePath);
        assert catObjsFolder.isDirectory();

        File hypertyFolder = new File(catObjsFolder, "hyperty");
        File stubFolder = new File(catObjsFolder, "protocolstub");
        File runtimeFolder = new File(catObjsFolder, "runtime");
        File schemaFolder = new File(catObjsFolder, "dataschema");
        File idpProxyFolder = new File(catObjsFolder, "idpproxy");

        resultMap.put(HYPERTY_MODEL_ID, generateInstances(hypertyFolder));
        resultMap.put(PROTOSTUB_MODEL_ID, generateInstances(stubFolder));
        resultMap.put(RUNTIME_MODEL_ID, generateInstances(runtimeFolder));
        resultMap.put(SCHEMA_MODEL_ID, generateInstances(schemaFolder));
        resultMap.put(IDPPROXY_MODEL_ID, generateInstances(idpProxyFolder));

        // gather sourcePackages, update sourcePackageURL
        LinkedList<RethinkInstance> sourcePackageInstances = new LinkedList<>();
        for (Map.Entry<Integer, RethinkInstance[]> entry : resultMap.entrySet()) {
            for (RethinkInstance instance : entry.getValue()) {
                RethinkInstance sourcePackage = instance.getSourcePackage();
                String objectName = instance.nameValueMap.get("objectName");
                if (instance.nameValueMap.get("sourcePackage") != null) {
                    instance.nameValueMap.put("sourcePackageURL", String.format("%s%s/%s/sourcepackage", accessURL, MODEL_ID_TO_NAME_MAP.get(entry.getKey()), objectName));
                } else if (sourcePackage != null) {
                    sourcePackage.nameValueMap.put("objectName", objectName);
                    sourcePackageInstances.add(sourcePackage);
                    instance.nameValueMap.put("sourcePackageURL", String.format("%ssourcepackage/%s", accessURL, objectName));
                }
            }
        }

        resultMap.put(SOURCEPACKAGE_MODEL_ID, sourcePackageInstances.toArray(new RethinkInstance[sourcePackageInstances.size()]));

        return resultMap;
    }

    private RethinkInstance[] generateInstances(File typeFolder) {
        Set<RethinkInstance> instances = new HashSet<>();

        if (typeFolder.exists()) {
            for (File dir : typeFolder.listFiles(dirFilter)) {
                try {
                    RethinkInstance instance = parseCatalogueObject(dir);
                    instances.add(instance);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return instances.toArray(new RethinkInstance[instances.size()]);
    }


    private RethinkInstance createFromFile(File f) throws FileNotFoundException {
        JsonObject descriptor = parser.parse(new FileReader(f)).getAsJsonObject();
        LinkedHashMap<String, String> nameValueMap = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : descriptor.entrySet()) {
            String value;
            try {
                value = entry.getValue().getAsString();
            } catch (Exception e) {
                value = entry.getValue().toString();
            }
            nameValueMap.put(entry.getKey(), value);
        }

        LOG.debug(String.format("parsed %s/%s:\r\n%s", f.getParentFile().getName(), f.getName(), gson.toJson(nameValueMap)));
        return new RethinkInstance(nameValueMap);
    }

    /**
     * InstanceEnabler for reTHINK resources.
     */
    public static class RethinkInstance extends BaseInstanceEnabler {

        /**
         * Set id:name map so instance knows which id corresponds to the correct field. (based on model.json)
         *
         * @param idNameMap
         */
        public void setIdNameMap(Map<Integer, String> idNameMap) {
            this.idNameMap = idNameMap;
        }

        private Map<Integer, String> idNameMap = null;
        private final Map<String, String> nameValueMap;
        private String sourceCodeKeyName = "sourceCode";
        private File sourceCodeFile = null;

        public RethinkInstance getSourcePackage() {
            return sourcePackage;
        }

        public void setSourcePackage(RethinkInstance sourcePackage) {
            this.sourcePackage = sourcePackage;
        }

        private RethinkInstance sourcePackage = null;

        public RethinkInstance() {
            idNameMap = null;
            nameValueMap = null;
        }

        public RethinkInstance(Map<Integer, String> idNameMap, Map<String, String> NameValueMap) {
            this.idNameMap = idNameMap;
            this.nameValueMap = NameValueMap;
        }

        /**
         * Create a reTHINK instance with name:value mapping from model.json
         *
         * @param nameValueMap
         */
        public RethinkInstance(Map<String, String> nameValueMap) {
            this.nameValueMap = nameValueMap;
        }


        @Override
        public ValueResponse read(int resourceid) {
            //LOG.debug("Read on Catalogue Resource " + resourceid);
            String resourceName = idNameMap.get(resourceid);
            String resourceValue = null;
            //LOG.debug("idNameMap returns: " + resourceName);

            try {
                if (sourceCodeFile != null && resourceName.equals(sourceCodeKeyName)) {
                    //LOG.debug("getting sourceCode from file: " + sourceCodeFile);
                    resourceValue = new Scanner(new FileInputStream(sourceCodeFile), "UTF-8").useDelimiter("\\A").next();
                } else {
                    resourceValue = nameValueMap.get(resourceName);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            //LOG.debug("nameValueMap returns: " + resourceValue);

            LOG.debug(String.format("(%s) Read on %02d->%s: %s", nameValueMap.get(NAME_FIELD_NAME), resourceid, resourceName, resourceValue));
            if (resourceValue != null) {
                //LOG.debug("returning: " + resourceValue);
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid, Value.newStringValue(resourceValue)));
            } else
                return super.read(resourceid);
        }

        public void setSourceCodeFile(File sourceFile) {
            sourceCodeFile = sourceFile;
        }

        @Override
        public String toString() {
            return nameValueMap.toString();
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

    private FileFilter dirFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory();
        }
    };

}