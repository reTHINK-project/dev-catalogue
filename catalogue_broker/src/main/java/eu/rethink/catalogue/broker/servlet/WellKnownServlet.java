/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.broker.servlet;

import eu.rethink.catalogue.broker.RequestHandler;
import eu.rethink.catalogue.broker.coap.WellKnownCoapResource;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A reTHINK specific HTTP servlet for handling requests on /.well-known/*
 */
public class WellKnownServlet extends HttpServlet {
    private RequestHandler requestHandler;
    private static final Logger LOG = LoggerFactory.getLogger(WellKnownServlet.class);

    public WellKnownServlet(LeshanServer server, RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        server.getCoapServer().add(new WellKnownCoapResource(requestHandler));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.info("GOT GET");
        String response = requestHandler.handleGET(req.getRequestURI());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().format(response).flush();
    }
}
