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

    public void start(String httpPort, String coapAddress, String coapsAddress) {
        // Try to use ENV variables if coap/coaps address is not given
        if (coapAddress == null)
            coapAddress = System.getenv("COAPIFACE");

        if (coapsAddress == null)
            coapsAddress = System.getenv("COAPSIFACE");

        // Build LWM2M server
        LeshanServerBuilder builder = new LeshanServerBuilder();
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

        if (httpPort == null) {
            httpPort = System.getenv("PORT");
            if (httpPort == null || httpPort.isEmpty()) {
                httpPort = System.getProperty("PORT");
            }
            if (httpPort == null || httpPort.isEmpty()) {
                httpPort = "8080";
            }
        }

        server = new Server(Integer.valueOf(httpPort));

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
        String httpPort = null, coapAddress = null, coapsAddress = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            arg = arg.toLowerCase();

            switch(arg) {
                case "-h":
                    // hand it down
                case "-http":
                    // hand it down
                case "-httpport":
                    httpPort = args[++i];

                    // if http address was given (like for coap), extract only port part
                    try {
                        httpPort = httpPort.substring(httpPort.lastIndexOf(':') + 1, httpPort.length());
                    } catch (IndexOutOfBoundsException e) {
//                        e.printStackTrace();
                    }
                    break;
                case "-c":
                    // hand it down
                case "-coap":
                    // hand it down
                case "-coapaddress":
                    coapAddress = args[++i];
                    break;
                case "-cs":
                    // hand it down
                case "-coaps":
                    // hand it down
                case "-coapsaddress":
                    coapsAddress = args[++i];
                    break;
            }
        }


        new CatalogueBroker().start(httpPort, coapAddress, coapsAddress);
    }
}
