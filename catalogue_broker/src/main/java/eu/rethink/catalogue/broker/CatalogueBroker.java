/*
  Copyright [2015-2017] Fraunhofer Gesellschaft e.V., Institute for
  Open Communication Systems (FOKUS)

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package eu.rethink.catalogue.broker;

import eu.rethink.catalogue.broker.config.BrokerConfig;
import eu.rethink.catalogue.broker.model.RethinkModelProvider;
import eu.rethink.catalogue.broker.servlet.EventServlet;
import eu.rethink.catalogue.broker.servlet.WellKnownServlet;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
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

    private BrokerConfig config;

    /**
     * Create Catalogue Broker instance with the given configuration
     *
     * @param config - configuration object for the Catalogue Broker
     */
    public CatalogueBroker(BrokerConfig config) {
        LOG.info("Catalogue Broker Version {}", getClass().getPackage().getImplementationVersion());
        if (config == null) {
            LOG.warn("Catalogue Broker started without BrokerConfig! Using default...");
            config = new BrokerConfig();
        }

        this.config = config;
    }

    public static void main(String[] args) {
        BrokerConfig brokerConfig = BrokerConfig.fromFile();
        brokerConfig.parseArgs(args);

        CatalogueBroker broker = new CatalogueBroker(brokerConfig);
        broker.start();
    }

    /**
     * Start the Catalogue Broker
     */
    public void start() {
        LOG.info("Starting Catalogue Broker with: {}", config.toString());

        // setup SLF4JBridgeHandler needed for proper logging
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        if (config.logLevel == 2) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration conf = ctx.getConfiguration();
            conf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.DEBUG);
            conf.getRootLogger().setLevel(Level.WARN);
            ctx.updateLoggers(conf);
        } else if (config.logLevel == 1) {
            LoggerContext vctx = (LoggerContext) LogManager.getContext(false);
            Configuration vconf = vctx.getConfiguration();
            vconf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.TRACE);
            vconf.getRootLogger().setLevel(Level.INFO);
            vctx.updateLoggers(vconf);
        } else if (config.logLevel == 0) {
            LoggerContext vctx = (LoggerContext) LogManager.getContext(false);
            Configuration vconf = vctx.getConfiguration();
            vconf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.TRACE);
            vconf.getRootLogger().setLevel(Level.TRACE);
            vctx.updateLoggers(vconf);
        }

        // set custom Californium settings
        NetworkConfig.createStandardWithoutFile();
        NetworkConfig.getStandard().setString(NetworkConfig.Keys.DEDUPLICATOR, NetworkConfig.Keys.DEDUPLICATOR_CROP_ROTATION);
        NetworkConfig.getStandard().setInt(NetworkConfig.Keys.PREFERRED_BLOCK_SIZE, 1024);

        // Build LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder();
        builder.setObjectModelProvider(new RethinkModelProvider());
        builder.setLocalAddress(config.coapHost, config.coapPort);
        builder.setLocalSecureAddress(config.coapHost, config.coapsPort);

        lwServer = builder.build();

        try {
            LOG.info("Starting CoAP Server...");
            lwServer.start();
        } catch (Exception e) {
            LOG.error("CoAP Server error", e);
            System.exit(1);
        }

        // Now prepare and start jetty
        server = new Server();

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.addCustomizer(new SecureRequestCustomizer());

        // === jetty-http.xml ===
        ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory(http_config));
        http.setHost(config.host);
        http.setPort(config.httpPort);
        http.setIdleTimeout(30000);
        server.addConnector(http);

        // === jetty-https.xml ===
        // SSL Context Factory
        SslContextFactory sslContextFactory = new SslContextFactory();

        Resource keystore = Resource.newClassPathResource("/keystore");
        if (keystore != null && keystore.exists() && config.keystorePath == null && config.truststorePath == null) {
            sslContextFactory.setKeyStoreResource(keystore);
            sslContextFactory.setTrustStoreResource(keystore);
        } else {
            if (config.keystorePath == null || config.truststorePath == null) {
                LOG.warn("Missing keystorePath or truststorePath for SSL configuration");
            }
            sslContextFactory.setKeyStorePath(config.keystorePath);
            sslContextFactory.setTrustStorePath(config.truststorePath);
        }
        sslContextFactory.setKeyStorePassword(config.keystorePassword);
        sslContextFactory.setKeyManagerPassword(config.keystoreManagerPassword);
        sslContextFactory.setTrustStorePassword(config.truststorePassword);
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
        sslConnector.setHost(config.host);
        sslConnector.setPort(config.httpsPort);
        server.addConnector(sslConnector);

        // rethink request handler
        RequestHandler rethinkRequestHandler = new RequestHandler(lwServer, config.defaultDescriptors);

        //ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", true, false);

        // WebApp stuff
        WebAppContext root = new WebAppContext();
        root.setContextPath("/");
        root.setResourceBase(getClass().getClassLoader().getResource("webapp").toExternalForm());
        //root.setParentLoaderPriority(true);

        ServletHolder servletHolder = new ServletHolder(new WellKnownServlet(rethinkRequestHandler));
        servletHolder.setAsyncSupported(true);
        root.addServlet(servletHolder, "/.well-known/*");

        ServletHolder eventServletHolder = new ServletHolder(new EventServlet(lwServer));
        eventServletHolder.setAsyncSupported(true);
        root.addServlet(eventServletHolder, "/event/*");
        server.setHandler(root);

        // Start jetty & webApp
        try {
            LOG.info("Starting HTTP Server...");
            server.start();
        } catch (Exception e) {
            LOG.error("HTTP Server error", e);
            System.exit(1);
        }

        LOG.info("Catalogue Broker started");

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }));
    }

    public void stop() {
        LOG.info("Stopping Catalogue Broker...");

        try {
            lwServer.stop();
            lwServer.destroy();
            server.stop();
            server.destroy();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
