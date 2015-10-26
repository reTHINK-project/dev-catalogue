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
package eu.rethink.catalogue.broker.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.client.Client;

import java.lang.reflect.Type;

public class ClientSerializer implements JsonSerializer<Client> {

    @Override
    public JsonElement serialize(Client src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject element = new JsonObject();

        element.addProperty("endpoint", src.getEndpoint());
        element.addProperty("registrationId", src.getRegistrationId());
        element.add("registrationDate", context.serialize(src.getRegistrationDate()));
        element.addProperty("address", src.getAddress().toString() + ":" + src.getPort());
        element.addProperty("smsNumber", src.getSmsNumber());
        element.addProperty("lwM2MmVersion", src.getLwM2mVersion());
        element.addProperty("lifetime", src.getLifeTimeInSec());
        element.addProperty("bindingMode", src.getBindingMode().toString());
        element.add("rootPath", context.serialize(src.getRootPath()));
        element.add("objectLinks", context.serialize(src.getSortedObjectLinks()));
        element.add("secure",
                context.serialize(src.getRegistrationEndpointAddress().getPort() == LeshanServerBuilder.PORT_DTLS));

        return element;
    }
}
