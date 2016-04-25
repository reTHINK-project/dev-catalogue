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


package eu.rethink.catalogue.broker;

import eu.rethink.catalogue.broker.model.RethinkModelProvider;
import eu.rethink.catalogue.broker.servlet.WellKnownServlet;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * The reTHINK Catalogue Broker
 */
public class CatalogueBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogueBroker.class);

    private Server server;
    private LeshanServer lwServer;
    private final int DEFAULT_HTTP_PORT = 80;
    private final int DEFAULT_SSL_PORT = 443;

    private int httpPort = DEFAULT_HTTP_PORT;
    private int sslPort = DEFAULT_SSL_PORT;
    private String coapAddress = null;

    public void setCoapsAddress(String coapsAddress) {
        this.coapsAddress = coapsAddress;
    }

    public void setCoapAddress(String coapAddress) {
        this.coapAddress = coapAddress;
    }

    public void setSslPort(int sslPort) {
        this.sslPort = sslPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    private String coapsAddress = null;

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public void setKeystoreManagerPassword(String keystoreManagerPassword) {
        this.keystoreManagerPassword = keystoreManagerPassword;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    private String keystorePath = "ssl/keystore";
    private String keystorePassword = "OBF:1vub1vnw1shm1y851vgl1vg91y7t1shw1vn61vuz";

    private String keystoreManagerPassword = "OBF:1vub1vnw1shm1y851vgl1vg91y7t1shw1vn61vuz";

    private String truststorePath = "ssl/keystore";
    private String truststorePassword = "OBF:1vub1vnw1shm1y851vgl1vg91y7t1shw1vn61vuz";

    public void start() {
        // check http ports
        if (httpPort < 0) {
            httpPort = DEFAULT_HTTP_PORT;
        }

        if (sslPort < 0) {
            sslPort = DEFAULT_SSL_PORT;
        }

        if (httpPort == DEFAULT_HTTP_PORT || httpPort == DEFAULT_SSL_PORT) {
            LOG.warn("Using default port for http or https. Please make sure you have the required privileges.");
        }

        // Build LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder();
        builder.setObjectModelProvider(new RethinkModelProvider());
        if (coapAddress != null && !coapAddress.isEmpty()) {
            // check if coapAddress is only port or host:port
            if (!coapAddress.contains(":")) {
                // only port -> prepend localhost
                coapAddress = "localhost:" + coapAddress;
            }

            builder.setLocalAddress(coapAddress.substring(0, coapAddress.lastIndexOf(':')),
                    Integer.parseInt(coapAddress.substring(coapAddress.lastIndexOf(':') + 1, coapAddress.length())));
        }


        if (coapsAddress != null && !coapsAddress.isEmpty()) {
            // check if coapsAddress is only port or host:port
            if (!coapsAddress.contains(":")) {
                // only port -> prepend localhost
                coapsAddress = "localhost:" + coapAddress;
            }
            builder.setLocalSecureAddress(coapsAddress.substring(0, coapsAddress.lastIndexOf(':')),
                    Integer.parseInt(coapsAddress.substring(coapsAddress.lastIndexOf(':') + 1, coapsAddress.length())));
        }

        lwServer = builder.build();
        lwServer.start();

        // Now prepare and start jetty
        server = new Server();

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.addCustomizer(new SecureRequestCustomizer());

        // === jetty-http.xml ===
        ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory(http_config));
        http.setPort(httpPort);
        http.setIdleTimeout(30000);
        server.addConnector(http);

        // === jetty-https.xml ===
        // SSL Context Factory

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(keystorePath);
        sslContextFactory.setKeyStorePassword(keystorePassword);
        sslContextFactory.setKeyManagerPassword(keystoreManagerPassword);
        sslContextFactory.setTrustStorePath(truststorePath);
        sslContextFactory.setTrustStorePassword(truststorePassword);
        sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

        // SSL HTTP Configuration
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        // SSL Connector
        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        sslConnector.setPort(sslPort);
        server.addConnector(sslConnector);

        // rethink request handler
        RequestHandler rethinkRequestHandler = new RequestHandler(lwServer);

        //ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(new WellKnownServlet(lwServer, rethinkRequestHandler));
        //servletContextHandler.addServlet(servletHolder, "/.well-known/*");

        // WebApp stuff
        WebAppContext root = new WebAppContext();
        root.setContextPath("/");
        root.setResourceBase(getClass().getClassLoader().getResource("webapp").toExternalForm());
        root.setParentLoaderPriority(true);
        root.addServlet(servletHolder, "/.well-known/*");

        server.setHandler(root);


        // Start jetty & webApp
        try {
            LOG.info("Server should be available at: " + server.getURI() + ". http on port " + httpPort + ", https on " + sslPort);
            LOG.info("Starting server...");
            server.start();
        } catch (Exception e) {
            LOG.error("jetty error", e);
        }
    }

    public void stop() {
        try {
            lwServer.destroy();
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CatalogueBroker broker = new CatalogueBroker();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            arg = arg.toLowerCase();

            switch (arg) {
                case "-httpport":
                case "-http":
                case "-h":
                    String rawHttpPort = args[++i];
                    int httpPort = -1;
                    // if http address was given (like for coap), extract only port part
                    try {
                        httpPort = Integer.parseInt(rawHttpPort.substring(rawHttpPort.lastIndexOf(':') + 1, rawHttpPort.length()));
                    } catch (IndexOutOfBoundsException e) {
                        //                        e.printStackTrace();
                    }
                    broker.setHttpPort(httpPort);
                    break;
                case "-httpsport":
                case "-https":
                case "-hs":
                case "-sslport":
                case "-ssl":
                case "-s":
                    broker.setSslPort(Integer.parseInt(args[++i]));
                    break;
                case "-coapaddress":
                case "-coap":
                case "-c":
                    broker.setCoapAddress(args[++i]);
                    break;
                case "-coapsaddress":
                case "-coaps":
                case "-cs":
                    broker.setCoapsAddress(args[++i]);
                    break;
                case "-keystorePath":
                case "-kp":
                    broker.setKeystorePath(args[++i]);
                    break;
                case "-truststorePath":
                case "-tp":
                    broker.setTruststorePath(args[++i]);
                    break;
                case "-keystorePassword":
                case "-kpw":
                    broker.setKeystorePassword(args[++i]);
                    break;
                case "-keyManagerPassword":
                case "-kmpw":
                    broker.setKeystoreManagerPassword(args[++i]);
                    break;
                case "-truststorePassword":
                case "-tpw":
                    broker.setTruststorePassword(args[++i]);
                    break;
                case "-v":
                    // increase log level
                    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                    Configuration conf = ctx.getConfiguration();
                    conf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.DEBUG);
                    ctx.updateLoggers(conf);
                    break;
            }
        }


        broker.start();
    }
}
