package eu.rethink.catalogue.database;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.response.ReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static eu.rethink.catalogue.database.Utils.*;

/**
 * InstanceEnabler for reTHINK Catalogue Object instances.
 */
public class CatalogueObjectInstance extends BaseInstanceEnabler {
    private transient Logger LOG;
    private JsonObject descriptor = null;
    private File sourceCode = null;
    private int model;
    private boolean isValid = true;
    private String name = "unknown";

    public CatalogueObjectInstance(int model, JsonObject descriptor, File sourceCode) {
        this.model = model;
        this.descriptor = descriptor;
        this.sourceCode = sourceCode;

        setup();
    }

    public CatalogueObjectInstance(int model, JsonObject descriptor) {
        this.model = model;
        this.descriptor = descriptor;

        setup();
    }

    public CatalogueObjectInstance() {
        setup();
    }

    public JsonObject getDescriptor() {
        return descriptor;
    }

    public File getSourceCode() {
        return sourceCode;
    }

    public int getModel() {
        return model;
    }

    public boolean isValid() {
        return isValid;
    }

    private String findName() {
        JsonElement name = descriptor.get("objectName");
        if (name == null)
            name = descriptor.get("cguid");

        if (name != null)
            this.name = name.getAsString();

        return this.name;
    }

    private void setup() {
        findName();
        LOG = LoggerFactory.getLogger(this.getClass().getPackage().getName() + "." + this.name);
        isValid = validate();

    }

    private boolean validate() {
        if (model == 0) {
            LOG.warn("Unable to validate instance: modelId not set!");
            return false;
        }

        if (descriptor == null) {
            LOG.warn("Unable to validate instance: descriptor json not set!");
            return false;
        }

        for (Map.Entry<Integer, ResourceModel> entry : objectModelMap.get(model).resources.entrySet()) {
            String name = entry.getValue().name;
            if (entry.getValue().mandatory && !descriptor.has(name) && !(name.equals("sourceCode") && sourceCode != null)) {
                LOG.warn("Validation of '{}' (has sourceCode file: {}) against model {} failed. '{}' is mandatory, but not included", gson.toJson(descriptor), sourceCode != null, model, entry.getValue().name);
                return false;
            }
        }
        LOG.trace("Validation against model {} succeeded", model);
        return true;
    }

    @Override
    public ReadResponse read(int resourceid) {
        String resourceName = MODEL_ID_TO_RESOURCES_MAP_MAP.get(model).get(resourceid).name;
        LOG.trace("Read on {} ({})", resourceid, resourceName);
        ReadResponse response;
        if (descriptor.has(resourceName)) {
            JsonElement element = descriptor.get(resourceName);
            response = ReadResponse.success(resourceid, element.isJsonPrimitive() ? element.getAsString() : element.toString());
        } else if (resourceName.equals("sourceCode")) {
            try {
                response = ReadResponse.success(resourceid, new String(Files.readAllBytes(Paths.get(sourceCode.toURI())), "UTF-8"));
            } catch (IOException e) {
                LOG.error("Unable to read sourceCode file of " + descriptor.toString(), e);
                response = ReadResponse.internalServerError("Unable to read sourceCode file: " + e.getMessage());
            }
        } else {
            response = ReadResponse.notFound();
        }
        return response;
    }
}