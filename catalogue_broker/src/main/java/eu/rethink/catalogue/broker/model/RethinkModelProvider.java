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
package eu.rethink.catalogue.broker.model;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * The Rethink Model Provider is almost identical to the Standard Model Provider, but also adds the custom models
 * from model.json
 */
public class RethinkModelProvider extends StandardModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RethinkModelProvider.class);

    private final LwM2mModel model;

    public RethinkModelProvider() {
        // build a single model with default objects
        List<ObjectModel> models = new LinkedList<>();

        // add custom models from model.json
        InputStream modelStream = getClass().getResourceAsStream("/model.json");
        models.addAll(ObjectLoader.loadJsonStream(modelStream));

        HashSet<ObjectModel> set = new HashSet<>();
        for (ObjectModel model : models) {
            //            LOG.debug("Loading object: {}", model);
            boolean isNew = set.add(model);
            if (!isNew) {
                LOG.debug("Model already exists for object {}. Overriding it.", model.id);
            }
        }
        this.model = new LwM2mModel(set);
    }

    @Override
    public LwM2mModel getObjectModel(Client client) {
        // same model for all clients
        return this.model;
    }
}
