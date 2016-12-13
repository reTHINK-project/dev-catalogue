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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import eu.rethink.catalogue.broker.RequestHandler;
import org.eclipse.leshan.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A reTHINK specific HTTP servlet for handling requests on /.well-known/*
 */
public class WellKnownServlet extends HttpServlet {
    private static Map<ResponseCode, Integer> coap2httpCodeMap = new HashMap<>();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    static {
        coap2httpCodeMap.put(ResponseCode.CREATED, HttpServletResponse.SC_CREATED);
        coap2httpCodeMap.put(ResponseCode.DELETED, HttpServletResponse.SC_OK);
        coap2httpCodeMap.put(ResponseCode.CHANGED, HttpServletResponse.SC_OK);
        coap2httpCodeMap.put(ResponseCode.CONTENT, HttpServletResponse.SC_OK);
        coap2httpCodeMap.put(ResponseCode.UNAUTHORIZED, HttpServletResponse.SC_FORBIDDEN);
        coap2httpCodeMap.put(ResponseCode.BAD_REQUEST, HttpServletResponse.SC_BAD_REQUEST);
        coap2httpCodeMap.put(ResponseCode.METHOD_NOT_ALLOWED, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        coap2httpCodeMap.put(ResponseCode.FORBIDDEN, HttpServletResponse.SC_FORBIDDEN);
        coap2httpCodeMap.put(ResponseCode.NOT_FOUND, HttpServletResponse.SC_NOT_FOUND);
        coap2httpCodeMap.put(ResponseCode.NOT_ACCEPTABLE, HttpServletResponse.SC_NOT_ACCEPTABLE);
        coap2httpCodeMap.put(ResponseCode.INTERNAL_SERVER_ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
    public WellKnownServlet(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        //server.getCoapServer().add(new WellKnownCoapResource(requestHandler));
        LOG.info("WellKnownServlet started");
    }

    /**
     * Handles incoming GET requests. Adds "Access-Control-Allow-Origin" header to response.
     */
    @Override
    protected void doGet(HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Received GET request on {}", req.getRequestURI());

        req.setCharacterEncoding("UTF-8");
        if (LOG.isTraceEnabled()) {
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                LOG.trace("has header: ({}:{})", headerName, req.getHeader(headerName));
            }

            Enumeration<String> attributeNames = req.getAttributeNames();
            while (attributeNames.hasMoreElements()) {
                String attributeName = attributeNames.nextElement();
                LOG.trace("has attribute: ({}:{})", attributeName, req.getAttribute(attributeName));
            }

            Enumeration<String> parameterNames = req.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                LOG.trace("has parameter: ({}:{})", parameterName, req.getParameter(parameterName));
            }
        }

        String host = req.getHeader("X-Forwarded-Host");
        if (host == null)
            host = req.getHeader("Host");

        resp.addHeader("Access-Control-Allow-Origin", "*");
        final AsyncContext asyncContext = req.startAsync();
        final String finalHost = host;
        asyncContext.start(new Runnable() {
            @Override
            public void run() {
                final ServletRequest aReq = asyncContext.getRequest();
                try {
                    aReq.setCharacterEncoding("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                final String path = String.valueOf(aReq.getAttribute("javax.servlet.async.request_uri"));
                // let it be handled by RequestHandler
                requestHandler.handleRequest(path, null, new RequestHandler.RequestCallback() {
                    @Override
                    public void result(RequestHandler.RequestResponse response) {
                        ServletResponse aResp = asyncContext.getResponse();
                        // set header so cross-domain requests work
                        Integer code = coap2httpCodeMap.get(response.getCode());

                        // try to map CoAP response code to http
                        if (code == null) {
                            LOG.warn("Unable to map coap response code {} to http; using default", response.getCode());
                            code = response.isSuccess() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        }
                        resp.setStatus(code);
                        // forward response
                        if (response.isSuccess()) {
                            try {
                                String jsonString = response.getJsonString(finalHost, path);
                                aResp.setContentType("application/json");
                                aResp.getWriter().write(jsonString);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            LOG.debug("returning error ({}): {}", code, response.getJsonString(finalHost, path));
                            try {
                                aResp.getWriter().write(response.getJsonString(finalHost, path));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        asyncContext.complete();
                    }
                });
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Received POST request on {}", req.getRequestURI());

        String host = req.getHeader("X-Forwarded-Host");
        if (host == null)
            host = req.getHeader("Host");

        resp.addHeader("Access-Control-Allow-Origin", "*");
        final AsyncContext asyncContext = req.startAsync();
        final String finalHost = host;
        asyncContext.start(new Runnable() {
            @Override
            public void run() {
                final ServletRequest aReq = asyncContext.getRequest();
                final ServletResponse aResp = asyncContext.getResponse();

                final String path = String.valueOf(aReq.getAttribute("javax.servlet.async.request_uri"));
                // let it be handled by RequestHandler

                JsonObject constraints = null;
                try {
                    constraints = gson.fromJson(aReq.getReader(), JsonObject.class);
                    LOG.trace("got Request with constraints: {}", gson.toJson(constraints));
                    requestHandler.handleRequest(path, constraints, new RequestHandler.RequestCallback() {
                        @Override
                        public void result(RequestHandler.RequestResponse response) {
                            // set header so cross-domain requests work
                            Integer code = coap2httpCodeMap.get(response.getCode());

                            // try to map CoAP response code to http
                            if (code == null) {
                                LOG.warn("Unable to map coap response code {} to http; using default", response.getCode());
                                code = response.isSuccess() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                            }
                            resp.setStatus(code);
                            // forward response
                            if (response.isSuccess()) {
                                try {
                                    String jsonString = response.getJsonString(finalHost, path);
                                    aResp.setContentType("application/json");
                                    aResp.getWriter().write(jsonString);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                LOG.debug("returning error ({}): {}", code, response.getJsonString(finalHost, path));
                                try {
                                    aResp.getWriter().write(response.getJsonString(finalHost, path));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            asyncContext.complete();
                        }
                    });
                } catch (Exception e) {
                    LOG.warn("Error while trying to parse constraints", e);
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    try {
                        aResp.getWriter().write("Unable to handle request:" + e.getLocalizedMessage());
                        aResp.getWriter().flush();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    asyncContext.complete();
                }

            }
        });
    }
}
