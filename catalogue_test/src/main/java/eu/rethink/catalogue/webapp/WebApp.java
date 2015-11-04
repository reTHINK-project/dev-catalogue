/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.webapp;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;


public class WebApp {

    private static int DEFAULT_PORT = 8080;

    public void start(int port) {
        // Prepare jetty
        Server server = new Server(port);
        WebAppContext root = new WebAppContext();
        root.setContextPath("/");
        root.setResourceBase(getClass().getClassLoader().getResource("webapp").toExternalForm());
        root.setParentLoaderPriority(true);


        server.setHandler(root);

        // Start jetty
        try {
            System.out.println("Starting WebApp on port " + port);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0 && !args[0].isEmpty())
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port could not be parsed. Please provide valid port", e);
//                e.printStackTrace();
            }


        WebApp webApp = new WebApp();
        webApp.start(port);
    }
}
