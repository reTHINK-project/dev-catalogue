### Developer view

As described in the [user view](./user_view.md) section, the reThink Catalogue consists of two functional components: the reThink Catalogue Broker and the reThink Catalogue Database.  The developer view describes per component the latter's functional block in the implementation.  Focus is herein given to those aspects of the implementation that need to be changed if a future revision of the reThink Catalogue is extended towards supporting additional catalgoue object types


#### reThink Catalogue Broker

The implementation of the reThink Catalogue Broker is concentrated in the catalogue broker class which only depends on the (a) the Eclipse Leshan (server) class and (b) on the http(s) server as provided via the Eclipse Jetty library.

![developer_view_broker](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-developer-view-broker.png)

The implementation of the catalogue broker class provides a main() method allowing to immediately derive a catalogue broker application. Within the main() method, the broker parses potential arguments provided during the invokation of the application. As the catalogue broker is an instantiated object at this time, all required initializations are completed upon invoking main(). For the developer, the initialization of the path to the key-files needed for https-support as well as the keys themself are essential. By default, the keystore at ssl/keystore is used. This can be changed with the appropriate launch arguments.
 
Upon ```start()```, the catalogue broker instantiates a Lehsan server object to provide the southbound interface towards the databases and to store the provided resource information from each connected database; and a http(s)-server that acts as the (additional) northbound interface as specified in the reThink Project.
 
Incoming (northbound) requests via http(s) on the ```/.well-known/*``` path as defined in the reThink resource path spec are handled via a servelet that invokes the ```rethinkRequestHandler = new RequestHandler(lwServer)```.  Note: The implementation of the RequestHandler class is contained in *RequestHandler.java*.

As the reThink resource path contains names, i.e. textual descripive tags, for each resource versus the core of the LWM2M-based catalogue employing unique numerical tags for efficiency, the ```RequestHandler``` generates a name:id map upon initialization for each supported resource.
```
for (Integer modelId : MODEL_IDS) {
    ObjectEnabler modelEnabler = new ObjectsInitializer(customModel).create(modelId);
    Map<Integer, ResourceModel> model = modelEnabler.getObjectModel().resources;
    MODEL_MAP.put(modelId, model);
    Map<String, Integer> resourceNameToIdMap = new LinkedHashMap<>(model.size());
    // populate id:name map from resources
    for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
        resourceNameToIdMap.put(entry.getValue().name, entry.getKey());
    }

    resourceNameToIdMapMap.put(modelId, resourceNameToIdMap);
    LOG.debug("generated name:id map for model " + modelId + ":\r\n" + gson.toJson(resourceNameToIdMap));
}
```

The ```handleGET()``` method parses the received (resource) path and returns either:

* a list of all registered objects of a certain type (if only the type was provided, e.g. `/.well-known/hyperty`)
   ```
    String type = pathParts[0];
    Integer id = MODEL_NAME_TO_ID_MAP.get(type);
    if (id != null) {
        return gson.toJson(nameToInstanceMapMap.get(id).keySet());
    } else {
        ... // error
    }
   ```
   
* or the requested catalogue object *or one of its resources* (e.g. `/.well-known/hyperty/FirstHyperty[/<resource>]`)
   ```
    if (resourceID != null) // resourceID is the LWM2M ID for the requested resource
        target += "/" + resourceID;
    
    String[] targetPaths = StringUtils.split(target, "/");
    Client client = server.getClientRegistry().get(targetPaths[0]);
    if (client != null) {
        ReadRequest request = new ReadRequest(StringUtils.removeStart(target, "/" + targetPaths[0]));
        ValueResponse response = server.send(client, request);
        if (!response.getCode().equals(ResponseCode.CONTENT)) {
            return encodeErrorResponse(createResponse(response.getCode(), "Unable to retrieve " + path));
        } else {
            return encodeResponse(response, modelType);
        }
    } else {
        ... // error
    }
   ```

#### reThink Catalogue Database

The implementation of the reThink Catalogue Database is concentrated in the catalogue database class which only depends on the Eclipse Leshan (client) class as seen in the figure below:

![developer_view_database](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-developer-view-database.png)


Upon initialization, the catalogue database checks if user-defined values for the (broker) host name and port number were given and if not, initializes them with default presets.

```
if (serverHostName == null)
    serverHostName = DEFAULT_SERVER_HOSTNAME;

if (serverPort == -1)
    serverPort = DEFAULT_SERVER_COAP_PORT;

if (catObjsPath == null)
    catObjsPath = "./catalogue_objects";
```
As seen, by default, all catalogue objects (e.g. hyperties, protostubs, etc) that are to be stored in the database are all contained in the directory structure below ```./catalogue_objects``` as contained in the rethink/dev-catalgue github repository.

The database holds a list of parsed instances mapped by the model ID:

```
// parse all catalogue objects
Map<Integer, RethinkInstance[]> resultMap = parseFiles(catObjsPath);
```

The ```parseFiles()``` method has internal knowledge about the supported *object types*:
```
File hypertyFolder = new File(catObjsFolder, "hyperty");
File stubFolder = new File(catObjsFolder, "protocolstub");
File runtimeFolder = new File(catObjsFolder, "runtime");
File schemaFolder = new File(catObjsFolder, "dataschema");
File idpProxyFolder = new File(catObjsFolder, "idpproxy");

resultMap.put(HYPERTY_MODEL_ID, generateInstances(hypertyFolder));
resultMap.put(PROTOSTUB_MODEL_ID, generateInstances(stubFolder));
resultMap.put(RUNTIME_MODEL_ID, generateInstances(runtimeFolder));
resultMap.put(SCHEMA_MODEL_ID, generateInstances(schemaFolder));
resultMap.put(IDPPROXY_MODEL_ID, generateInstances(idpProxyFolder));
```
and needs to be extended for the database to support additional object types. As all catalogue objects are internally treated by the reThink Catalogue Database as *resources*, a description of those custom resources has to be added to the list of default resources available in the Leshan Client that is later instantiated.  To make these custom resources available, the catalogue database reads a description of those custom resources and initializes the list of supported resource objects:
```
// get default models
List<ObjectModel> objectModels = ObjectLoader.loadDefault();

// add custom models from model.json
InputStream modelStream = getClass().getResourceAsStream("/model.json");
objectModels.addAll(ObjectLoader.loadJsonStream(modelStream));

// map object models by ID
HashMap<Integer, ObjectModel> map = new HashMap<>();
for (ObjectModel objectModel : objectModels) {
    map.put(objectModel.id, objectModel);
}

// Initialize object list
ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(map));
```

Please note that the description of custom resource objects as contained in the model.json file has to be altered when new catalogue object types are to be supported by the database.

Finally, the Leshan Client is instantiated and told to attach to the reThink Catalogue Broker upon start.  
```
// Create client
final InetSocketAddress clientAddress = new InetSocketAddress("0", 0);
final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

final LeshanClient client = new LeshanClient(clientAddress, serverAddress, new ArrayList<LwM2mObjectEnabler>(
        enablers));

// Start the client
client.start();

// Register to the server
LOG.info(String.format("Registering on %s...", serverAddress));
final String endpointIdentifier = UUID.randomUUID().toString();
RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));
...
```

To assure proper teardown of resources, the reThink Catalogue Database detaches from the Broker upon shut-down:
```
// Deregister on shutdown and stop client.
Runtime.getRuntime().addShutdownHook(new Thread() {
    @Override
    public void run() {
        if (registrationID != null) {
            LOG.info("Device: Deregistering Client '" + registrationID + "'");
            client.send(new DeregisterRequest(registrationID), 1000);
            client.stop();
        }
    }
});
```

As the reThink Catalogue Database used the Leshan Client implementation without any modifications, a developer that whishes to extend the reThink Catalogue Database is unlikely change of the Leshan Client code.  In case, modification of Leshan are required, a developer is kindly asked to consult the [Leshan documentation](https://github.com/eclipse/leshan).  It should be noted though that at this point, the reThink Catalogue Database only uses Lehsan (in terms of linking against the unmodified version of it) which provides the basis to provide the reThink Cataluge under the given license terms.  Any modifications that propagate outside the scope of the rethink/dev-catalouge repository need to be evaluated agains potential impact on licensing.

