/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.broker.model;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Rethink Model Provider is almost identical to the Standard Model Provider, but also adds the custom models
 * from model.json
 */
public class RethinkModelProvider extends StandardModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StandardModelProvider.class);

    private final LwM2mModel model;

    public RethinkModelProvider() {
        // build a single model with default objects
        List<ObjectModel> models = ObjectLoader.loadDefault();

        // add custom models from model.json
        InputStream modelStream = getClass().getResourceAsStream("/model.json");
        models.addAll(ObjectLoader.loadJsonStream(modelStream));

        Map<Integer, ObjectModel> map = new HashMap<>();
        for (ObjectModel model : models) {
            LOG.debug("Loading object: {}", model);
            ObjectModel old = map.put(model.id, model);
            if (old != null) {
                LOG.debug("Model already exists for object {}. Overriding it.", model.id);
            }
        }
        this.model = new LwM2mModel(map);
    }

    @Override
    public LwM2mModel getObjectModel(Client client) {
        // same model for all clients
        return model;
    }
}
