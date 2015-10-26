/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.broker.coap;

import eu.rethink.catalogue.broker.RequestHandler;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
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

    public WellKnownCoapResource(RequestHandler requestHandler) {
        super(path);
        this.requestHandler = requestHandler;
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        // TODO: let RequestHandler handle it and respond with the string
        exchange.respond(CoAP.ResponseCode.CONTENT, "Under Construction");
    }

    @Override
    public Resource getChild(String name) {
        LOG.info("REQUESTING CHILD: " + name);
        return this;
    }
}
