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
import org.eclipse.californium.core.network.config.NetworkConfig;
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

import java.io.*;

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
    private String host = null;
    private String coapHost = null;
    private String coapsHost = null;

    private int coapPort = 5683;
    private int coapsPort = 5684;

    public void setHost(String host) {
        this.host = host;
    }

    public void setCoapHost(String coapHost) {
        this.coapHost = coapHost;
    }

    public void setCoapsHost(String coapsHost) {
        this.coapsHost = coapsHost;
    }

    public void setCoapPort(int coapPort) {
        this.coapPort = coapPort;
    }

    public void setCoapsPort(int coapsPort) {
        this.coapsPort = coapsPort;
    }

    public void setSslPort(int sslPort) {
        this.sslPort = sslPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

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

        if (host != null && coapHost == null)
            coapHost = host;

        if (host != null && coapsHost == null)
            coapsHost = host;

        if (coapHost != null && coapsHost == null) {
            coapsHost = coapHost;
        }

        // set californium properties
        try {
            InputStream in = getClass().getResourceAsStream("/Californium.properties");
            OutputStream out = new FileOutputStream("Californium.properties.tmp");
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
            out.close();
            File f = new File("Californium.properties.tmp");
            NetworkConfig.createStandardWithFile(f);
            f.deleteOnExit();
        } catch (IOException e) {
            LOG.warn("Unable to use Californium properties from resources folder: {}", e);
        }

        // Build LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder();
        builder.setObjectModelProvider(new RethinkModelProvider());
        builder.setLocalAddress(coapHost, coapPort);
        builder.setLocalSecureAddress(coapsHost, coapsPort);

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
        http.setHost(host);
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
        sslConnector.setHost(host);
        sslConnector.setPort(sslPort);
        server.addConnector(sslConnector);

        // rethink request handler
        RequestHandler rethinkRequestHandler = new RequestHandler(lwServer);

        //ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(new WellKnownServlet(lwServer, rethinkRequestHandler));
        servletHolder.setAsyncSupported(true);
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
            LOG.info("Starting HTTP Server...");
            server.start();
        } catch (Exception e) {
            LOG.error("HTTP Server error", e);
            System.exit(1);
        }

        LOG.info("Catalogue Broker is running");
        LOG.info(" HTTP Host: " + server.getURI().getHost());
        LOG.info(" HTTP Port: " + httpPort);
        LOG.info("HTTPS Port: " + sslPort);
        if (coapHost != null)
            LOG.info(" CoAP Host: " + coapHost);
        if (coapsHost != null)
            LOG.info("CoAPs Host: " + coapsHost);
        LOG.info(" CoAP Port: " + coapPort);
        LOG.info("CoAPs Port: " + coapsPort);

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

    public static void main(String[] args) {
        // setup SLF4JBridgeHandler needed for proper logging
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CatalogueBroker broker = new CatalogueBroker();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            arg = arg.toLowerCase();

            switch (arg) {
                case "-host":
                    broker.setHost(args[++i]);
                    break;
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
                    String[] addr = args[++i].split(":");
                    broker.setCoapHost(addr[0]);
                    if (addr.length > 1)
                        broker.setCoapPort(Integer.parseInt(addr[1]));
                    else
                        LOG.warn("used -coapaddress without providing port!");
                    break;
                case "-coapsaddress":
                case "-coaps":
                case "-cs":
                    String[] sAddr = args[++i].split(":");
                    broker.setCoapsHost(sAddr[0]);
                    if (sAddr.length > 1)
                        broker.setCoapsPort(Integer.parseInt(sAddr[1]));
                    else
                        LOG.warn("used -coapsaddress without providing port!");
                    break;
                case "-coaphost":
                case "-ch":
                    broker.setCoapHost(args[++i]);
                    break;
                case "-coapport":
                case "-cp":
                    broker.setCoapPort(Integer.parseInt(args[++i]));
                    break;
                case "-coapshost":
                case "-csh":
                    broker.setCoapsHost(args[++i]);
                    break;
                case "-coapsport":
                case "-csp":
                    broker.setCoapsPort(Integer.parseInt(args[++i]));
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
                    conf.getRootLogger().setLevel(Level.INFO);
                    ctx.updateLoggers(conf);
                    break;
                case "-vv":
                    // increase log level
                    LoggerContext vctx = (LoggerContext) LogManager.getContext(false);
                    Configuration vconf = vctx.getConfiguration();
                    vconf.getLoggerConfig("eu.rethink.catalogue").setLevel(Level.DEBUG);
                    vconf.getRootLogger().setLevel(Level.DEBUG);
                    vctx.updateLoggers(vconf);
                    break;
            }
        }


        broker.start();
    }
}
