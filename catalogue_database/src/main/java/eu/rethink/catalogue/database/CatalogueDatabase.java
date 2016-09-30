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

import eu.rethink.catalogue.database.config.DatabaseConfig;
import eu.rethink.catalogue.database.exception.CatalogueObjectParsingException;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.LwM2mId;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.DmServerInfo;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import static eu.rethink.catalogue.database.Parser.ObjectsParser.parseObjects;
import static eu.rethink.catalogue.database.Utils.objectModels;
import static eu.rethink.catalogue.database.Utils.setLoggerLevel;
import static org.eclipse.leshan.client.object.Security.noSec;

/**
 * A reference implementation for a reTHINK Catalogue Database
 */
public class CatalogueDatabase {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogueDatabase.class);

    private LeshanClient client;
    private Thread hook = null;
    private DatabaseConfig config;

    /**
     * Create Catalogue Database instance with the given configuration
     *
     * @param config - configuration object for the Catalogue Database
     */
    public CatalogueDatabase(DatabaseConfig config) {
        LOG.info("Catalogue Database Version {}", getClass().getPackage().getImplementationVersion());
        if (config == null) {
            LOG.warn("Catalogue Broker started without BrokerConfig! Using default...");
            config = new DatabaseConfig();
        }

        this.config = config;
    }

    public static void main(final String[] args) throws CatalogueObjectParsingException {
        // create Catalogue Database configuration object and feed it with the arguments
        DatabaseConfig databaseConfig = DatabaseConfig.fromFile();
        databaseConfig.parseArgs(args);

        CatalogueDatabase database = new CatalogueDatabase(databaseConfig);
        database.start();
    }

    /**
     * Start the Catalogue Database
     */
    public void start() {
        LOG.info("Starting Catalogue Database: {}", config.toString());

        setLoggerLevel(config.logLevel);

        // set custom Californium settings
        NetworkConfig.createStandardWithoutFile();
        NetworkConfig.getStandard().setString(NetworkConfig.Keys.DEDUPLICATOR, NetworkConfig.Keys.DEDUPLICATOR_CROP_ROTATION);
        NetworkConfig.getStandard().setInt(NetworkConfig.Keys.PREFERRED_BLOCK_SIZE, 1024);

        // build leshan client
        LeshanClientBuilder builder = new LeshanClientBuilder(config.endpoint);
        // add broker address to intializer
        String serverURI = String.format("coap://%s:%s", config.brokerHost, config.brokerPort);
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(objectModels));

        // set SECURITY
        initializer.setInstancesForObject(LwM2mId.SECURITY, noSec(serverURI, 123));

        // set SERVER
        initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, config.lifeTime, BindingMode.U, false));

        // set DEVICE
        Device device = new Device();
        device.setDatabase(this);
        initializer.setInstancesForObject(LwM2mId.DEVICE, device);

        List<LwM2mObjectEnabler> enablers = initializer.create(LwM2mId.SECURITY, LwM2mId.SERVER, LwM2mId.DEVICE);

        // parse all catalogue objects
        File catObjs = new File(config.catalogueObjectsPath);
        // skip parsing if catalogue objects folder does not exist or is not a directory
        if (!catObjs.exists()) {
            LOG.warn("Catalogue Objects folder '" + catObjs.getAbsolutePath() + "' does not exist!");
        } else if (!catObjs.isDirectory()) {
            LOG.warn("Catalogue Objects folder '" + catObjs.getAbsolutePath() + "' is not a directory!");
        } else {
            LOG.info("Catalogue Objects folder location: {}", catObjs.getAbsolutePath());
            try {
                // parse catalogue objects
                Map<Integer, Set<CatalogueObjectInstance>> parsedObjects = parseObjects(catObjs);
                // Initialize object list

                for (Map.Entry<Integer, Set<CatalogueObjectInstance>> entry : parsedObjects.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        //LOG.debug("Setting instances: {}", gson.toJson(entry.getValue()));
                        initializer.setInstancesForObject(entry.getKey(), entry.getValue().toArray(new CatalogueObjectInstance[entry.getValue().size()]));
                        enablers.add(initializer.create(entry.getKey()));
                    }
                    LOG.debug("Setting {} instance(s) for model {}", String.format("%02d", entry.getValue().size()), entry.getKey());

                }

            } catch (CatalogueObjectParsingException e) {
                //e.printStackTrace();
                LOG.warn("Parsing exception!", e);
            }
        }

        builder.setObjects(enablers);
        builder.setLocalAddress(config.coapHost, config.coapPort);
        builder.setLocalSecureAddress(config.coapHost, config.coapsPort);

        client = builder.build();
        client.addObserver(observer);
        // Start the client
        client.start();
        LOG.info("Catalogue Database is running. Registering...");
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

    /**
     * Stop leshan client
     */
    public void stop() {
        LOG.info("Stopping Catalogue Database");
        if (client != null) {
            client.destroy(true);
        }
    }

    private LwM2mClientObserverAdapter observer = new LwM2mClientObserverAdapter() {
        @Override
        public void onRegistrationSuccess(DmServerInfo dmServerInfo, String s) {
            LOG.info("Successfully registered on '{}'", dmServerInfo.getFullUri().toString());
        }
    };

    /**
     * Provides the default device description of a Database instance.
     */
    public static class Device extends BaseInstanceEnabler {

        private static String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());
        private static String timeZone = TimeZone.getDefault().getID();
        private CatalogueDatabase database = null;

        public Device() {

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

        private static String getUtcOffset() {
            return utcOffset;
        }

        private static void setUtcOffset(String t) {
            utcOffset = t;
        }

        private static String getTimezone() {
            return timeZone;
        }

        private void setTimezone(String t) {
            timeZone = t;
        }

        private static String getSupportedBinding() {
            return "U";
        }

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
            LOG.info("Device restart requested");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (database != null) {
                        database.stop();
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

    }

}