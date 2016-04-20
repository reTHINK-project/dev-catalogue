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
import org.eclipse.leshan.LwM2mId;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.eclipse.leshan.client.object.Security.noSec;


/**
 * A reference implementation for a reTHINK Catalogue Database
 */
public class CatalogueDatabase {
    private String registrationID;
    private static final Logger LOG = LoggerFactory.getLogger(CatalogueDatabase.class);

    public static CatalogueDatabase instance;

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


    private final String DEFAULT_SERVER_HOSTNAME = "localhost";
    private final int DEFAULT_SERVER_COAP_PORT = 5683;
    private LeshanClient client;

    private String serverHostName = DEFAULT_SERVER_HOSTNAME;
    private String serverDomain = null;
    private String accessURL = null;
    private String catObjsPath = "./catalogue_objects";
    private int serverPort = DEFAULT_SERVER_COAP_PORT;
    private boolean useHttp = false;
    private int lifetime = 60;

    private String endpoint = "DB_" + new Random().nextInt(Integer.MAX_VALUE);

    private Thread hook = null;

    public void setUseHttp(boolean useHttp) {
        this.useHttp = useHttp;
    }

    public void setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
    }

    public void setServerDomain(String serverDomain) {
        this.serverDomain = serverDomain;
    }

    public void setCatObjsPath(String catObjsPath) {
        this.catObjsPath = catObjsPath;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    public static void main(final String[] args) {
        CatalogueDatabase d = new CatalogueDatabase();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "-host":
                    d.setServerHostName(args[++i]);
                    break;
                case "-usehttp":
                    if (args.length <= i + 1 || args[i + 1].startsWith("-")) { // check if boolean value is not given, assume true
                        d.setUseHttp(true);
                    } else {
                        d.setUseHttp(Boolean.parseBoolean(args[++i]));
                    }
                    break;
                case "-p":
                case "-port":
                    d.setServerPort(Integer.parseInt(args[++i]));
                    break;
                case "-o":
                case "-op":
                case "-objPath":
                case "-objpath":
                    d.setCatObjsPath(args[++i]);
                    break;
                case "-d":
                case "-domain":
                    d.setServerDomain(args[++i]);
                    break;
                case "-lifetime":
                case "-t":
                    d.setLifetime(Integer.parseInt(args[++i]));
                    break;
            }
        }

        d.start();
    }

    /**
     * Start the Catalogue Database.
     */
    public void start() {
        LOG.info("Starting Catalogue Database...");

        if (serverDomain == null) {
            serverDomain = serverHostName;
        }

        if (useHttp) {
            accessURL = "http://" + serverDomain + "/.well-known/";
        } else {
            accessURL = "https://" + serverDomain + "/.well-known/";
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

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(objectModels));

        // add broker address to intializer
        String serverURI = String.format("coap://%s:%s", serverHostName, serverPort);

        initializer.setInstancesForObject(LwM2mId.SECURITY, noSec(serverURI, 123));
        initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, lifetime, BindingMode.U, false));

        // set dummy Device
        Device device = new Device();
        device.setDatabase(this);
        initializer.setInstancesForObject(LwM2mId.DEVICE, device);

        List<LwM2mObjectEnabler> enablers = initializer.create(LwM2mId.SECURITY, LwM2mId.SERVER, LwM2mId.DEVICE);


        // iterate through supported models,
        // set parsed instances of those models in initializer,
        // and finally give the instances the id:name map from the model
        for (Integer modelId : MODEL_IDS) {
            RethinkInstance[] instances = resultMap.get(modelId);

            if (instances != null && instances.length > 0) {
                //LOG.debug("setting instances: {}", gson.toJson(instances));
                initializer.setInstancesForObject(modelId, instances);
                LwM2mObjectEnabler enabler = initializer.create(modelId);
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

        LOG.info("I am '{}'", endpoint);

        LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
        builder.setObjects(enablers);

        client = builder.build();

        // Start the client
        client.start();

        final CatalogueDatabase ref = this;

        // Deregister on shutdown and stop client.
        // add hook only if not set already (important if database was restarted using execute command)
        if (hook == null) {
            hook = new Thread() {
                @Override
                public void run() {
                    ref.stop();
                }
            };
            Runtime.getRuntime().addShutdownHook(hook);
        }
    }

    public void stop() {
        LOG.info("Device: Deregistering Client");
        if (client != null)
            client.destroy(true);
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
                if (sourcePackage != null) {
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
        RethinkInstance sourcePackage = null;
        if (pkg.exists()) {
            sourcePackage = createFromFile(pkg);
        } else if (instance.nameValueMap.containsKey("sourcePackage")) {
            JsonObject jSourcePackage = gson.fromJson(instance.nameValueMap.get("sourcePackage"), JsonObject.class);
            sourcePackage = createFromJson(jSourcePackage);

            // remove if from nameValueMap, because it will be added with instance.setSourcePackage(sourcePackage);
            instance.nameValueMap.remove("sourcePackage");
        }

        if (sourcePackage != null) {
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

            // add sourcePackage to instance
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
        return createFromJson(descriptor);
    }

    /**
     * Creates a RethinkInstance from a JsonObject
     *
     * @param descriptor - JsonObject that defines the catalogue object
     * @return A RethinkInstance based on the information of the parsed JsonObect
     * @throws FileNotFoundException
     */
    private RethinkInstance createFromJson(JsonObject descriptor) throws FileNotFoundException {
        LinkedHashMap<String, String> nameValueMap = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : descriptor.entrySet()) {
            String value;
            if (entry.getValue().isJsonPrimitive())
                value = entry.getValue().getAsString();
            else if (entry.getValue().isJsonArray())
                value = entry.getValue().getAsJsonArray().toString();
            else
                value = entry.getValue().toString();

            nameValueMap.put(entry.getKey(), value);
        }

        LOG.debug("parsed descriptor: {}", gson.toJson(nameValueMap));
        return new RethinkInstance(nameValueMap);
    }


    /**
     * InstanceEnabler for reTHINK resources.
     */
    public static class RethinkInstance extends BaseInstanceEnabler {

        private Map<Integer, String> idNameMap = null;
        private Map<String, String> nameValueMap = null;

        private String sourceCodeKeyName = "sourceCode";

        // sourceCode file if this instance is a sourcePackage
        private File sourceCodeFile = null;

        // attached sourcePackage if this instance is a catalogue object
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

        private String getName() {
            return nameValueMap.containsKey(NAME_FIELD_NAME) ? nameValueMap.get(NAME_FIELD_NAME) : nameValueMap.get("cguid");
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
        public ReadResponse read(int resourceid) {
            String resourceName = idNameMap.get(resourceid);
            String resourceValue = null;
            try {
                if (sourceCodeFile != null && resourceName.equals(sourceCodeKeyName)) {
                    //LOG.debug("getting sourceCode from file: " + sourceCodeFile);
                    resourceValue = new String(Files.readAllBytes(Paths.get(sourceCodeFile.toURI())), "UTF-8");
                } else {
                    resourceValue = nameValueMap.get(resourceName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            //LOG.debug("nameValueMap returns: " + resourceValue);
            LOG.debug(String.format("(%s) Read on %02d -> %s %s",
                    getName(),
                    resourceid,
                    resourceName,
                    resourceValue != null ? "[OK]" : "[NOT FOUND]"
            ));
            if (resourceValue != null) {
                return ReadResponse.success(resourceid, resourceValue);
            } else {
                return super.read(resourceid);
            }
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

        }

        private CatalogueDatabase database = null;

        public void setDatabase(CatalogueDatabase database) {
            this.database = database;
        }

        @Override
        public ReadResponse read(int resourceid) {
            LOG.debug("Read on Device Resource " + resourceid);
            switch (resourceid) {
                case 0:
                    return ReadResponse.success(resourceid, getManufacturer());
                case 1:
                    return ReadResponse.success(resourceid, getModelNumber());
                case 2:
                    return ReadResponse.success(resourceid, getSerialNumber());
                case 3:
                    return ReadResponse.success(resourceid, getFirmwareVersion());
                case 9:
                    return ReadResponse.success(resourceid, getBatteryLevel());
                case 10:
                    return ReadResponse.success(resourceid, getMemoryFree());
                case 11:
                    return ReadResponse.success(resourceid, getErrorCode());
                case 13:
                    return ReadResponse.success(resourceid, getCurrentTime());
                case 14:
                    return ReadResponse.success(resourceid, getUtcOffset());
                case 15:
                    return ReadResponse.success(resourceid, getTimezone());
                case 16:
                    return ReadResponse.success(resourceid, getSupportedBinding());
                default:

                    return super.read(resourceid);
            }
        }

        @Override
        public ExecuteResponse execute(int resourceid, String params) {
            LOG.debug("Execute on Device resource ({}, {})", resourceid, params);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (database != null) {
                        database.stop();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        database.start();
                    } else {
                        LOG.warn("database reference not set!");
                    }
                }
            }).start();
            if (database != null) {
                return ExecuteResponse.success();
            } else {
                return ExecuteResponse.internalServerError("Missing Database reference. Please set via setDatabase()");
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            LOG.debug("Write on Device resource ({}, {})", resourceid, value);
            return super.write(resourceid, value);
        }

        private static String getManufacturer() {
            return "Rethink Example Catalogue";
        }

        private static String getModelNumber() {
            return "Model 1337";
        }

        private static String getSerialNumber() {
            return "RT-500-000-0001";
        }

        private static String getFirmwareVersion() {
            return "0.1.0";
        }

        private static int getErrorCode() {
            return 0;
        }

        private static int getBatteryLevel() {
            final Random rand = new Random();
            return rand.nextInt(100);
        }

        private static int getMemoryFree() {
            final Random rand = new Random();
            return rand.nextInt(50) + 114;
        }

        private static Date getCurrentTime() {
            return new Date();
        }

        private static String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());

        private static String getUtcOffset() {
            return utcOffset;
        }

        private static void setUtcOffset(String t) {
            utcOffset = t;
        }

        private static String timeZone = TimeZone.getDefault().getID();

        private static String getTimezone() {
            return timeZone;
        }

        private void setTimezone(String t) {
            timeZone = t;
        }

        private static String getSupportedBinding() {
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