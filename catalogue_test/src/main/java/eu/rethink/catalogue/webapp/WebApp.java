/************************************************************************************
 * Copyright (c) 2015 -- 2017 by Fraunhofer Fokus
 ************************************************************************************/
package eu.rethink.catalogue.webapp;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;


public class WebApp {

    public void start() {
        // Prepare jetty
        int port = 8090;
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
        WebApp webApp = new WebApp();
        webApp.start();
    }
}
