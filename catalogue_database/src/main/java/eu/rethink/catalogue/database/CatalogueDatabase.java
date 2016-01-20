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

    private final int HYPERTY_MODEL_ID          = 1337;
    private final int PROTOSTUB_MODEL_ID        = 1338;
    private final int SOURCEPACKAGE_MODEL_ID    = 1339;
    private String accessURL;

    public static void main(final String[] args) {
        switch (args.length) {
            case 0:
                // hand down
            case 1:
                System.out
                        .println("Usage:\njava -jar target/catalogue_database-*-jar-with-dependencies.jar ServerHost ServerCoapPort [ObjectFolderPath]");
                break;
            case 2:
                new CatalogueDatabase(args[0], Integer.parseInt(args[1]), null);
                break;
            case 3:
                new CatalogueDatabase(args[0], Integer.parseInt(args[1]), args[2]);
                break;
        }
    }

    public CatalogueDatabase(final String serverHostName, final int serverPort, String catObjsPath) {

        // parse files
        RethinkInstance[] parsedHyperties;
        RethinkInstance[] parsedProtostubs;
        RethinkInstance[] parsedSourcePackages;

        if (catObjsPath == null) {
            catObjsPath = "./";
        }

        accessURL = "https://" + serverHostName + "/.well-known/";

        Map<Integer, RethinkInstance[]> resultMap = parseFiles(catObjsPath);
        parsedHyperties = resultMap.get(HYPERTY_MODEL_ID);
        parsedProtostubs = resultMap.get(PROTOSTUB_MODEL_ID);
        parsedSourcePackages = resultMap.get(SOURCEPACKAGE_MODEL_ID);

        LOG.debug("parsedHyperties" + Arrays.toString(resultMap.get(HYPERTY_MODEL_ID)));
        LOG.debug("parsedProtostubs" + Arrays.toString(resultMap.get(PROTOSTUB_MODEL_ID)));
        LOG.debug("parsedSourcePackages" + Arrays.toString(resultMap.get(SOURCEPACKAGE_MODEL_ID)));

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

        if (parsedHyperties.length > 0) {
            initializer.setInstancesForObject(HYPERTY_MODEL_ID, parsedHyperties);
            ObjectEnabler hypertyEnabler = initializer.create(HYPERTY_MODEL_ID);
            enablers.add(hypertyEnabler);

            // create idNameMap for hyperties
            LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
            Map<Integer, ResourceModel> model = hypertyEnabler.getObjectModel().resources;

            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                idNameMap.put(entry.getKey(), entry.getValue().name);
            }

            // set id:name map on all hyperties
            LOG.debug("hyperties get this idNameMap: " + idNameMap);
            for (RethinkInstance parsedHyperty : parsedHyperties) {
                parsedHyperty.setIdNameMap(idNameMap);
            }
        }

        if (parsedProtostubs.length > 0) {
            initializer.setInstancesForObject(PROTOSTUB_MODEL_ID, parsedProtostubs);
            ObjectEnabler protostubEnabler = initializer.create(PROTOSTUB_MODEL_ID);
            enablers.add(protostubEnabler);

            // create idNameMap for protostubs
            LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
            Map<Integer, ResourceModel> model = protostubEnabler.getObjectModel().resources;

            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                idNameMap.put(entry.getKey(), entry.getValue().name);
            }

            // set id:name map on all protostubs
            for (RethinkInstance parsedProtostub : parsedProtostubs) {
                parsedProtostub.setIdNameMap(idNameMap);
            }

        }

        if (parsedSourcePackages.length > 0) {
            initializer.setInstancesForObject(SOURCEPACKAGE_MODEL_ID, parsedSourcePackages);
            ObjectEnabler sourcePackageEnabler = initializer.create(SOURCEPACKAGE_MODEL_ID);
            enablers.add(sourcePackageEnabler);

            // create idNameMap for protostubs
            LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
            Map<Integer, ResourceModel> model = sourcePackageEnabler.getObjectModel().resources;

            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                idNameMap.put(entry.getKey(), entry.getValue().name);
            }

            // set id:name map on all protostubs
            for (RethinkInstance parcedSourcePackage : parsedSourcePackages) {
                parcedSourcePackage.setIdNameMap(idNameMap);
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
        File code = new File(dir, "sourceCode.json");

        // 1. parse hyperty
        RethinkInstance hyperty = createFromFile(desc);

        // 2. parse sourcePackage
        if (pkg.exists()) {
            RethinkInstance sourcePackage = createFromFile(pkg);

            // 3. attach code to sourcePackage
            if (code.exists()) {
                sourcePackage.setSourceCodeFile(code);
            }

            // 4. add sourcePackage to hyperty
            hyperty.setSourcePackage(sourcePackage);
        }
        return hyperty;
    }

    private Map<Integer, RethinkInstance[]> parseFiles(String sourcePath) {
        // 1. open file, 2. parse hyperties.json
        if (sourcePath == null)
            sourcePath = "./";

        HashMap<Integer, RethinkInstance[]> resultMap = new HashMap<>();
        Gson gson = new Gson();

        File catObjsFolder = new File(sourcePath);
        assert catObjsFolder.isDirectory();

        File hypertyFolder = new File(catObjsFolder, "hyperty");
        File stubFolder = new File(catObjsFolder, "protocolstub");

        LinkedList<RethinkInstance> hypertyInstances = new LinkedList<>();
        LinkedList<RethinkInstance> stubInstances = new LinkedList<>();
        if (hypertyFolder.exists()) {
            for (File dir : hypertyFolder.listFiles(dirFilter)) {
                try {
                    RethinkInstance hypertyInstance = parseCatalogueObject(dir);
                    hypertyInstances.add(hypertyInstance);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        resultMap.put(HYPERTY_MODEL_ID, hypertyInstances.toArray(new RethinkInstance[hypertyInstances.size()]));

        if (stubFolder.exists()) {
            for (File dir : stubFolder.listFiles(dirFilter)) {
                try {
                    RethinkInstance stubInstance = parseCatalogueObject(dir);
                    stubInstances.add(stubInstance);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        resultMap.put(PROTOSTUB_MODEL_ID, stubInstances.toArray(new RethinkInstance[stubInstances.size()]));

        LinkedList<RethinkInstance> sourcePackageInstances = new LinkedList<>();
        int i = 0;
        for (RethinkInstance hypertyInstance : hypertyInstances) {
            RethinkInstance sourcePackage = hypertyInstance.getSourcePackage();
            if (sourcePackage != null) {
                String hypertyName = hypertyInstance.nameValueMap.get("objectName");
                sourcePackage.nameValueMap.put("objectName", hypertyName);
                sourcePackageInstances.add(sourcePackage);
                hypertyInstance.nameValueMap.put("sourcePackageURL", accessURL + "sourcepackage/" + hypertyName);
            }
        }
        for (RethinkInstance stubInstance : stubInstances) {
            RethinkInstance sourcePackage = stubInstance.getSourcePackage();
            if (sourcePackage != null) {
                String stubName = stubInstance.nameValueMap.get("objectName");
                sourcePackage.nameValueMap.put("objectName", stubName);

                sourcePackageInstances.add(sourcePackage);
                stubInstance.nameValueMap.put("sourcePackageURL", accessURL + "sourcepackage/" + stubName);
            }
        }
        resultMap.put(SOURCEPACKAGE_MODEL_ID, sourcePackageInstances.toArray(new RethinkInstance[sourcePackageInstances.size()]));


        return resultMap;
    }


    private RethinkInstance createFromFile(File f) throws FileNotFoundException {
        JsonObject descriptor = parser.parse(new FileReader(f)).getAsJsonObject();
        LinkedHashMap<String, String> nameValueMap = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : descriptor.entrySet()) {
            String value = null;
            try {
                value = entry.getValue().getAsString();

            } catch (IllegalStateException e) {
                value = entry.getValue().toString();
            }

            nameValueMap.put(entry.getKey(), value);
        }

        LOG.debug("instance gets this nameValueMap:\n" + nameValueMap);
        return new RethinkInstance(nameValueMap);
    }

    /**
     * InstanceEnabler for reTHINK resources.
     */
    public static class RethinkInstance extends BaseInstanceEnabler {

        /**
         * Set id:name map so instance knows which id corresponds to the correct field. (based on model.json)
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
         * @param nameValueMap
         */
        public RethinkInstance(Map<String, String> nameValueMap) {
            this.nameValueMap = nameValueMap;
        }


        @Override
        public ValueResponse read(int resourceid) {
            LOG.debug("Read on Catalogue Resource " + resourceid);
            String resourceName = idNameMap.get(resourceid);
            String resourceValue = null;
            LOG.debug("idNameMap returns: " + resourceName);

            try {
                if (sourceCodeFile != null && resourceName.equals(sourceCodeKeyName)) {
                    LOG.debug("getting sourceCode from file: " + sourceCodeFile);
                    resourceValue = new Scanner(new FileInputStream(sourceCodeFile), "UTF-8").useDelimiter("\\A").next();
                } else {
                    resourceValue = nameValueMap.get(resourceName);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            LOG.debug("nameValueMap returns: " + resourceValue);

            if (resourceValue != null) {
                LOG.debug("returning: " + resourceValue);
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid, Value.newStringValue(resourceValue)));
            } else
                return super.read(resourceid);
        }

        public void setSourceCodeFile(File sourceFile) {
            sourceCodeFile = sourceFile;
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