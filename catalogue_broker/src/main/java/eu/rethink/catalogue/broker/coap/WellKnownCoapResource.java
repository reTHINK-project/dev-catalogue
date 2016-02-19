/**
 * Copyright [2015-2017] Fraunhofer Gesellschaft e.V., Institute for
 * Open Communication Systems (FOKUS)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/


package eu.rethink.catalogue.broker.coap;

import eu.rethink.catalogue.broker.RequestHandler;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reTHINK specific CoAP resource for handling requests on /.well-known/*
 */
public class WellKnownCoapResource extends CoapResource {
    private static final String path = ".well-known";
    private static final Logger LOG = LoggerFactory.getLogger(WellKnownCoapResource.class);
    private RequestHandler requestHandler;

    /**
     * Create proxy coap resource that takes all requests for /.well-known/ and its sub-resources.
     * Does not handle requests itself.
     * Pushes incoming requests into request handler and returns the result to the request source.
     * @param requestHandler handles requests on /.well-known/*
     */
    public WellKnownCoapResource(RequestHandler requestHandler) {
        super(path);
        this.requestHandler = requestHandler;
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        String resp = requestHandler.handleGET(exchange.getRequestOptions().getUriPathString());
        exchange.respond(resp);
    }

    /**
     * Always returns itself so the handle* methods are guaranteed to be called by this instance.
     */
    @Override
    public Resource getChild(String name) {
//        LOG.info("REQUESTING CHILD: " + name);
        return this;
    }
}
