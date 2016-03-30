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

    // model IDs that define the custom models inside model.json
    private static final int HYPERTY_MODEL_ID = 1337;
    private static final int PROTOSTUB_MODEL_ID = 1338;
    private static final int RUNTIME_MODEL_ID = 1339;
    private static final int SCHEMA_MODEL_ID = 1340;
    private static final int IDPPROXY_MODEL_ID = 1341;

    private static final int SOURCEPACKAGE_MODEL_ID = 1350;

    // list of supported models
    private static final Set<Integer> MODEL_IDS = new HashSet<>(Arrays.asList(HYPERTY_MODEL_ID, PROTOSTUB_MODEL_ID, RUNTIME_MODEL_ID, SCHEMA_MODEL_ID, IDPPROXY_MODEL_ID, SOURCEPACKAGE_MODEL_ID));

    // path names for the models
    private static final String HYPERTY_TYPE_NAME = "hyperty";
    private static final String PROTOSTUB_TYPE_NAME = "protocolstub";
    private static final String RUNTIME_TYPE_NAME = "runtime";
    private static final String SCHEMA_TYPE_NAME = "dataschema";
    private static final String IDPPROXY_TYPE_NAME = "idp-proxy";

    private static final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";

    // mapping of model IDs to their path names
    private static Map<Integer, String> MODEL_ID_TO_NAME_MAP = new HashMap<>();

    static {
        MODEL_ID_TO_NAME_MAP.put(HYPERTY_MODEL_ID, HYPERTY_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(PROTOSTUB_MODEL_ID, PROTOSTUB_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(RUNTIME_MODEL_ID, RUNTIME_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(SCHEMA_MODEL_ID, SCHEMA_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(IDPPROXY_MODEL_ID, IDPPROXY_TYPE_NAME);
        MODEL_ID_TO_NAME_MAP.put(SOURCEPACKAGE_MODEL_ID, SOURCEPACKAGE_TYPE_NAME);
    }

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JsonParser parser = new JsonParser();

    private static final String NAME_FIELD_NAME = "objectName";
    private static final String CGUID_FIELD_NAME = "cguid";

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

    /**
     * Create and start a Catalogue Database.
     *
     * @param serverHostName - Catalogue Broker host name, e.g. "mydomain.com" or "127.0.0.1"
     * @param serverPort     - Catalogue Broker access port, by default 5683
     * @param catObjsPath    - path to the folder that contains the catalogue objects
     * @param useHttp        - whether or not to use http or https when generating the sourcePackageURLs
     */
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

        // parse all catalogue objects
        Map<Integer, RethinkInstance[]> resultMap = parseFiles(catObjsPath);

        // get default models
        List<ObjectModel> objectModels = ObjectLoader.loadDefault();

        // add custom models from model.json
        InputStream modelStream = getClass().getResourceAsStream("/model.json");
        objectModels.addAll(ObjectLoader.loadJsonStream(modelStream));

        // map object models by ID
        HashMap<Integer, ObjectModel> map = new HashMap<>();
        for (ObjectModel objectModel : objectModels) {
            map.put(objectModel.id, objectModel);
        }

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(map));

        // set dummy Device
        initializer.setClassForObject(3, Device.class);
        List<ObjectEnabler> enablers = initializer.createMandatory();

        // iterate through supported models,
        // set parsed instances of those models in initializer,
        // and finally give the instances the id:name map from the model
        for (Integer modelId : MODEL_IDS) {
            RethinkInstance[] instances = resultMap.get(modelId);

            if (instances != null) {
                initializer.setInstancesForObject(modelId, instances);
                ObjectEnabler enabler = initializer.create(modelId);
                enablers.add(enabler);

                // create id:name map for the current model
                LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
                Map<Integer, ResourceModel> model = enabler.getObjectModel().resources;

                // populate id:name map from resources
                for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                    idNameMap.put(entry.getKey(), entry.getValue().name);
                }

                // set id:name map on all instances of that model
                for (RethinkInstance instance : instances) {
                    instance.setIdNameMap(idNameMap);
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

    /**
     * Parse all catalogue objects contained in this folder and their respective subfolders
     *
     * @param sourcePath - path to the catalogue objects source folder (e.g. catalogue_objects)
     * @return Map containing all parsed objects as instances, mapped by model ID
     */
    private Map<Integer, RethinkInstance[]> parseFiles(String sourcePath) {
        if (sourcePath == null)
            sourcePath = "./";

        HashMap<Integer, RethinkInstance[]> resultMap = new HashMap<>();

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
                if (instance.nameValueMap.get("sourcePackage") != null) {
                    instance.nameValueMap.put("sourcePackageURL", String.format("%s%s/%s/sourcePackage", accessURL, MODEL_ID_TO_NAME_MAP.get(entry.getKey()), instance.nameValueMap.get(NAME_FIELD_NAME)));
                } else if (sourcePackage != null) {
                    String cguid = instance.nameValueMap.get(CGUID_FIELD_NAME);
                    sourcePackage.nameValueMap.put("cguid", cguid);
                    sourcePackageInstances.add(sourcePackage);
                    instance.nameValueMap.put("sourcePackageURL", String.format("%ssourcepackage/%s", accessURL, cguid));
                }
            }
        }

        resultMap.put(SOURCEPACKAGE_MODEL_ID, sourcePackageInstances.toArray(new RethinkInstance[sourcePackageInstances.size()]));

        //LOG.debug("parsed files: {}", gson.toJson(resultMap));
        return resultMap;
    }

    /**
     * Parse catalogue objects contained in the given type folder (e.g. catalogue_objects/hyperty)
     *
     * @param typeFolder - folder that contains catalogue objects as subfolders (e.g. catalogue_objects/hyperty)
     * @return Array of parsed catalogue objects as instances
     */
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


    /**
     * Parse catalogue object from this folder
     *
     * @param dir - folder that contains the catalogue object files (e.g. catalogue_objects/hyperty/FirstHyperty)
     * @return instance of the parsed catalogue object
     * @throws FileNotFoundException
     */
    private RethinkInstance parseCatalogueObject(File dir) throws FileNotFoundException {
        File desc = new File(dir, "description.json");
        File pkg = new File(dir, "sourcePackage.json");

        // 1. parse catalogue object
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
            // 4. add sourcePackage to catalogue object
            instance.setSourcePackage(sourcePackage);
        }
        return instance;
    }

    /**
     * Creates a RethinkInstance from a description file (or sourcePackage file)
     *
     * @param f - file that holds the json that defines the catalogue object
     * @return A RethinkInstance based on the information of the parsed file
     * @throws FileNotFoundException
     */
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

        private Map<Integer, String> idNameMap = null;
        private Map<String, String> nameValueMap = null;

        private String sourceCodeKeyName = "sourceCode";

        // source code file if this instance is a sourcePackage
        private File sourceCodeFile = null;

        // source package if this instance is a catalogue object
        private RethinkInstance sourcePackage = null;


        /**
         * Set id:name map so instance knows which id corresponds to the correct field. (based on model.json)
         *
         * @param idNameMap - id:name mapping of the model this is an instance of (based on model.json)
         */
        public void setIdNameMap(Map<Integer, String> idNameMap) {
            this.idNameMap = idNameMap;
        }

        /**
         * Return the sourcePackage that belongs to this instance
         *
         * @return sourcePackage - source package that belongs to this (descriptor) instance
         */
        public RethinkInstance getSourcePackage() {
            return sourcePackage;
        }

        /**
         * Attach sourcePackage instance to this (descriptor) instance
         *
         * @param sourcePackage - sourcePackage that belongs to this (descriptor) instance
         */
        public void setSourcePackage(RethinkInstance sourcePackage) {
            this.sourcePackage = sourcePackage;
        }

        /**
         * Set the source code file for this sourcePackage instance
         *
         * @param sourceFile - file that contains the source code of this sourcePackage
         */
        public void setSourceCodeFile(File sourceFile) {
            sourceCodeFile = sourceFile;
        }


        /**
         * Create a reTHINK instance with name:value mapping from a parsed catalogue object file
         *
         * @param nameValueMap - name:value mapping based on parsed catalogue object file
         */
        public RethinkInstance(Map<String, String> nameValueMap) {
            this.nameValueMap = nameValueMap;
        }

        /**
         * Create a reTHINK instance. Be aware that you still need to give it a nameValueMap and a idNameMap
         */
        public RethinkInstance() {
            nameValueMap = null;
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

            LOG.debug(String.format("(%s) Read on %02d->%s: %s", nameValueMap.containsKey(NAME_FIELD_NAME) ? nameValueMap.get(NAME_FIELD_NAME) : nameValueMap.get("cguid"), resourceid, resourceName, resourceValue));
            if (resourceValue != null) {
                //LOG.debug("returning: " + resourceValue);
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid, Value.newStringValue(resourceValue)));
            } else
                return super.read(resourceid);
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