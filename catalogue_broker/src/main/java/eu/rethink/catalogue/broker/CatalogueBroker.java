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

import eu.rethink.catalogue.broker.servlet.WellKnownServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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

    public void start() {
        // Use those ENV variables for specifying the interface to be bound for coap and coaps
        String iface = System.getenv("COAPIFACE");
        String ifaces = System.getenv("COAPSIFACE");

        // Build LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder();
        if (iface != null && !iface.isEmpty()) {
            builder.setLocalAddress(iface.substring(0, iface.lastIndexOf(':')),
                    Integer.parseInt(iface.substring(iface.lastIndexOf(':') + 1, iface.length())));
        }
        if (ifaces != null && !ifaces.isEmpty()) {
            builder.setLocalAddressSecure(ifaces.substring(0, ifaces.lastIndexOf(':')),
                    Integer.parseInt(ifaces.substring(ifaces.lastIndexOf(':') + 1, ifaces.length())));
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
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = System.getProperty("PORT");
        }
        if (webPort == null || webPort.isEmpty()) {
            webPort = "8080";
        }
        server = new Server(Integer.valueOf(webPort));

        // rethink request handler
        RequestHandler rethinkRequestHandler = new RequestHandler(lwServer);

        ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(new WellKnownServlet(lwServer, rethinkRequestHandler));
        servletContextHandler.addServlet(servletHolder, "/.well-known/*");

        // Start jetty
        try {
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
        new CatalogueBroker().start();
    }
}
