/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.broker.coap;

import eu.rethink.catalogue.broker.RequestHandler;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.core.response.ValueResponse;
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
        ValueResponse resp = requestHandler.handleGET(exchange.getRequestOptions().getUriPathString());
        exchange.respond(requestHandler.encodeResponse(resp));
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
