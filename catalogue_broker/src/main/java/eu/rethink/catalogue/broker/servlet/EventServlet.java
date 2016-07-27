package eu.rethink.catalogue.broker.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.jetty.servlets.EventSourceServlet;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.eclipse.leshan.server.client.ClientUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * EventServlet that can be used to get notifications about connected clients/databases on the Broker
 */
public class EventServlet extends EventSourceServlet {
    private static final Logger LOG = LoggerFactory.getLogger(EventServlet.class);

    private static final String EVENT_DEREGISTRATION = "DEREGISTRATION";

    private static final String EVENT_UPDATED = "UPDATED";

    private static final String EVENT_REGISTRATION = "REGISTRATION";

    private static final String EVENT_NOTIFICATION = "NOTIFICATION";

    private static final String EVENT_CLIENT_LIST = "CLIENTS";

    private static final String EVENT_COAP_LOG = "COAPLOG";

    private static final String QUERY_PARAM_ENDPOINT = "ep";

    private final LeshanServer server;

    private final Gson gson = new GsonBuilder().create();

    private Set<Event> events = new ConcurrentHashSet<>();

    private final ClientRegistryListener clientRegistryListener = new ClientRegistryListener() {
        private void sendNotify() {
            sendEvent(gson.toJson(getEndpoints()));
        }

        @Override
        public void registered(Client client) {
            sendNotify();
        }

        @Override
        public void updated(ClientUpdate clientUpdate, Client client) {
            //sendNotify();
        }

        @Override
        public void unregistered(Client client) {
            sendNotify();
        }
    };


    /**
     * This EventServlet will notify browser clients about changes of connected Databases
     *
     * @param server - leshan server which will be observed for client changes
     */
    public EventServlet(LeshanServer server) {
        this.server = server;
        server.getClientRegistry().addListener(this.clientRegistryListener);
        LOG.debug("EventServlet started");
    }

    /**
     * Send some data to subscribed browser clients
     *
     * @param data - Data to send, that will be received with eventSource.onmessage
     */
    private synchronized void sendEvent(String data) {
        for (Event e : events) {
            try {
                e.sendData(data);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }


    @Override
    protected EventSource newEventSource(HttpServletRequest req) {
        // someone subscribed to this event source
        LOG.debug("newEventSource: {}", req);
        return new Event();
    }

    /**
     * Get an array of names of connected Catalogue Databases on the Broker
     *
     * @return list of Database names
     */
    private String[] getEndpoints() {
        Collection<Client> clients = server.getClientRegistry().allClients();
        Iterator<Client> iterator = clients.iterator();
        String[] endpoints = new String[clients.size()];
        int i = 0;
        // iterate through connected clients and extract name
        while (iterator.hasNext()) {
            Client next = iterator.next();
            endpoints[i++] = next.getEndpoint();
        }
        //LOG.debug("generated endpoint list: {}", gson.toJson(endpoints));
        return endpoints;
    }

    /**
     * Small class that encapsulates the emitters
     */
    private class Event implements EventSource {
        private Emitter emitter;

        @Override
        public void onOpen(final Emitter emitter) {
            LOG.debug("onOpen " + this.hashCode());
            this.emitter = emitter;
            events.add(this);
            try {
                emitter.data(gson.toJson(getEndpoints()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onClose() {
            LOG.debug("onClose " + this.hashCode());
            events.remove(this);
        }

        public void sendData(String data) throws IOException {
            LOG.debug("sendData " + this.hashCode());
            emitter.data(data);
        }
    }
}

