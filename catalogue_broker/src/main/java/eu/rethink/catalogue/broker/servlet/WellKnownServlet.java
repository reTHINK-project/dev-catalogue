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

package eu.rethink.catalogue.broker.servlet;

import eu.rethink.catalogue.broker.RequestHandler;
import eu.rethink.catalogue.broker.coap.WellKnownCoapResource;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A reTHINK specific HTTP servlet for handling requests on /.well-known/*
 */
public class WellKnownServlet extends HttpServlet {
    private static Map<ResponseCode, Integer> coap2httpCodeMap = new HashMap<>();

    static {
        coap2httpCodeMap.put(ResponseCode.CONTENT, HttpServletResponse.SC_OK);
        coap2httpCodeMap.put(ResponseCode.CHANGED, HttpServletResponse.SC_OK);
        coap2httpCodeMap.put(ResponseCode.INTERNAL_SERVER_ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        coap2httpCodeMap.put(ResponseCode.METHOD_NOT_ALLOWED, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        coap2httpCodeMap.put(ResponseCode.NOT_FOUND, HttpServletResponse.SC_NOT_FOUND);
        coap2httpCodeMap.put(ResponseCode.UNAUTHORIZED, HttpServletResponse.SC_UNAUTHORIZED);
    }


    private RequestHandler requestHandler;
    private static final Logger LOG = LoggerFactory.getLogger(WellKnownServlet.class);

    /**
     * Create proxy http servlet that takes all requests for /.well-known/ and its sub-resources.
     * Does not handle requests itself.
     * Pushes incoming requests into request handler and returns the result to the request source.
     *
     * @param requestHandler handles requests on /.well-known/*
     */
    public WellKnownServlet(LeshanServer server, RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        server.getCoapServer().add(new WellKnownCoapResource(requestHandler));
    }

    /**
     * Handles incoming GET requests. Adds "Access-Control-Allow-Origin" header to response.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.info("GOT GET");

        RequestHandler.RequestResponse response = requestHandler.handleGET(req.getRequestURI());
        resp.addHeader("Access-Control-Allow-Origin", "*");
        Integer code = coap2httpCodeMap.get(response.getCode());
        if (code == null) {
            LOG.warn("HTTP Code is null! Coap code: {}", response.getCode());
            code = response.isSuccess() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        if (response.isSuccess()) {
            resp.setStatus(code);
            resp.getWriter().write(response.getJsonResponse());
        } else {
            resp.sendError(code, response.getJsonResponse());
        }
    }


}
