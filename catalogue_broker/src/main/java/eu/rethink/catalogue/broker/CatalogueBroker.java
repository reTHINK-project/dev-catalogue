/*******************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 * <p/>
 * This contribution was based on the contribution from the leshan repository
 * on github (master branch as of 20151023).
 * <p/>
 * The copyright and list of contributors of the original, baseline contribution
 * is kept below
 * <p/>
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * <p/>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * <p/>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.html.
 * <p/>
 * Contributors:
 * Sierra Wireless - initial API and implementation
 *******************************************************************************/

/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package eu.rethink.catalogue.broker;

import eu.rethink.catalogue.broker.model.RethinkModelProvider;
import eu.rethink.catalogue.broker.servlet.WellKnownServlet;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;

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

            builder.setLocalAddressSecure(coapsAddress.substring(0, coapsAddress.lastIndexOf(':')),
                    Integer.parseInt(coapsAddress.substring(coapsAddress.lastIndexOf(':') + 1, coapsAddress.length())));
        }

        // Get public and private server key
        PrivateKey privateKey = null;
        PublicKey publicKey = null;
        try {
            // Get point values
            byte[] publicX = DatatypeConverter
                    .parseHexBinary("fcc28728c123b155be410fc1c0651da374fc6ebe7f96606e90d927d188894a73");
            byte[] publicY = DatatypeConverter
                    .parseHexBinary("d2ffaa73957d76984633fc1cc54d0b763ca0559a9dff9706e9f4557dacc3f52a");
            byte[] privateS = DatatypeConverter
                    .parseHexBinary("1dae121ba406802ef07c193c1ee4df91115aabd79c1ed7f4c0ef7ef6a5449400");

            // Get Elliptic Curve Parameter spec for secp256r1
            AlgorithmParameters algoParameters = AlgorithmParameters.getInstance("EC");
            algoParameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec parameterSpec = algoParameters.getParameterSpec(ECParameterSpec.class);

            // Create key specs
            KeySpec publicKeySpec = new ECPublicKeySpec(new ECPoint(new BigInteger(publicX), new BigInteger(publicY)),
                    parameterSpec);
            KeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(privateS), parameterSpec);

            // Get keys
            publicKey = KeyFactory.getInstance("EC").generatePublic(publicKeySpec);
            privateKey = KeyFactory.getInstance("EC").generatePrivate(privateKeySpec);

//            builder.setSecurityRegistry(new SecurityRegistryImpl(privateKey, publicKey));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidParameterSpecException e) {
            LOG.warn("Unable to load RPK.", e);
        }


        lwServer = builder.build();
        lwServer.start();

        // Now prepare and start jetty
        server = new Server();

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
//        http_config.setSecureScheme("https");
//        http_config.setSecurePort(8443);
//        http_config.setOutputBufferSize(32768);
//        http_config.setRequestHeaderSize(8192);
//        http_config.setResponseHeaderSize(8192);
//        http_config.setSendServerVersion(true);
//        http_config.setSendDateHeader(false);
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

        ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(new WellKnownServlet(lwServer, rethinkRequestHandler));
        servletContextHandler.addServlet(servletHolder, "/.well-known/*");

        // Start jetty
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
        CatalogueBroker broker = new CatalogueBroker();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            arg = arg.toLowerCase();

            switch(arg) {
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
            }
        }


        broker.start();
    }
}
