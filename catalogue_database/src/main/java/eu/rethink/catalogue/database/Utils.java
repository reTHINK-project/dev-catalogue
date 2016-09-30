package eu.rethink.catalogue.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.InputStream;
import java.util.*;

/**
 * Created by Robert Ende on 29.09.16.
 */
public class Utils {

    // model IDs that define the custom models inside model.json
    public static final int HYPERTY_MODEL_ID = 1337;
    public static final int PROTOSTUB_MODEL_ID = 1338;
    public static final int RUNTIME_MODEL_ID = 1339;
    public static final int SCHEMA_MODEL_ID = 1340;
    public static final int IDPPROXY_MODEL_ID = 1341;
    public static final int SOURCEPACKAGE_MODEL_ID = 1350;

    // list of supported models
    public static final Set<Integer> MODEL_IDS = new LinkedHashSet<>(Arrays.asList(HYPERTY_MODEL_ID, PROTOSTUB_MODEL_ID, RUNTIME_MODEL_ID, SCHEMA_MODEL_ID, IDPPROXY_MODEL_ID, SOURCEPACKAGE_MODEL_ID));

    // path names for the models
    public static final String HYPERTY_TYPE_NAME = "hyperty";
    public static final String PROTOSTUB_TYPE_NAME = "protocolstub";
    public static final String RUNTIME_TYPE_NAME = "runtime";
    public static final String SCHEMA_TYPE_NAME = "dataschema";
    public static final String IDPPROXY_TYPE_NAME = "idp-proxy";

    public static final String SOURCEPACKAGE_TYPE_NAME = "sourcepackage";
    public static final String NAME_FIELD_NAME = "objectName";
    public static final String CGUID_FIELD_NAME = "cguid";

    public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // mapping of model IDs to their path names
    public static final Map<String, Integer> MODEL_NAME_TO_ID_MAP = new LinkedHashMap<>(6);

    public static Map<Integer, ObjectModel> objectModelMap = new HashMap<>(MODEL_IDS.size());
    public static Map<Integer, Map<Integer, ResourceModel>> MODEL_ID_TO_RESOURCES_MAP_MAP = new LinkedHashMap<>();

    // get default models
    public static List<ObjectModel> objectModels = ObjectLoader.loadDefault();

    static {
        // setup SLF4JBridgeHandler needed for proper logging
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        MODEL_NAME_TO_ID_MAP.put(HYPERTY_TYPE_NAME, HYPERTY_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(PROTOSTUB_TYPE_NAME, PROTOSTUB_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(RUNTIME_TYPE_NAME, RUNTIME_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(SCHEMA_TYPE_NAME, SCHEMA_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(IDPPROXY_TYPE_NAME, IDPPROXY_MODEL_ID);
        MODEL_NAME_TO_ID_MAP.put(SOURCEPACKAGE_TYPE_NAME, SOURCEPACKAGE_MODEL_ID);

        // add custom models from model.json
        List<ObjectModel> customObjectModels = getCustomObjectModels();
        objectModels.addAll(customObjectModels);

        // setup objectModelMap
        for (ObjectModel objectModel : customObjectModels) {
            objectModelMap.put(objectModel.id, objectModel);
        }

        // setup MODEL_ID_TO_RESOURCES_MAP
        for (ObjectModel customObjectModel : customObjectModels) {
            MODEL_ID_TO_RESOURCES_MAP_MAP.put(customObjectModel.id, customObjectModel.resources);
        }
    }

    public static JsonParser parser = new JsonParser();

    /**
     * Load and return custom Object Models from models.json
     *
     * @return List of ObjectModels
     */
    public static List<ObjectModel> getCustomObjectModels() {
        InputStream modelStream = Utils.class.getResourceAsStream("/model.json");
        return ObjectLoader.loadJsonStream(modelStream);
    }

    public static void setLoggerLevel(int level) {
        if (level == 2) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration conf = ctx.getConfiguration();
            conf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.DEBUG);
            conf.getRootLogger().setLevel(Level.WARN);
            ctx.updateLoggers(conf);
        } else if (level == 1) {
            LoggerContext vctx = (LoggerContext) LogManager.getContext(false);
            Configuration vconf = vctx.getConfiguration();
            vconf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.TRACE);
            vconf.getRootLogger().setLevel(Level.INFO);
            vctx.updateLoggers(vconf);
        } else if (level == 0) {
            LoggerContext vctx = (LoggerContext) LogManager.getContext(false);
            Configuration vconf = vctx.getConfiguration();
            vconf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.TRACE);
            vconf.getRootLogger().setLevel(Level.TRACE);
            vctx.updateLoggers(vconf);
        }
    }

}
