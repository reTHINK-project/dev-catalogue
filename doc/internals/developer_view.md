### Developer view
* specifications, interfaces
* including usable tools, tutorials and working examples
* 


As described in the [user view](./user_view.md) section, the reThink Catalogue consists of two functional components: the reThink Catalogue Broker and the reThink Catalogue Database.  The developer view describes per component the latter's functional block in the implementation.  Focus is herein given to those aspects of the implementation that need to be changed if a future revision of the reThink Catalogue is extended towards:
  * supporting additional catalgoue object kinds
  * tbf.


#### reThink Catalogue Broker

The implementation of the reThink Catalogue Broker is concentrated in the catalogue broker class which only depends on the (a) the Eclipse Leshan (server) class and (b) on the http(s) server as provided via the Java library.

XXXX include Fig Here

The implementation of the catalogue broker class provides a main() method allowing to immediately derive a catalogue broker application.  Within the main() method, the broker parses potential arguments provided during the invokation of the application.  As the catalogue broker is an instantiated object at this time, all required initializations are completed upon invoking main().  For the developer, the initialization of the path to the key-files needed for https-support as well as the keys themself are essential.  In the current implentation, both are hard-coded, i.e.:

    private String keystorePath = "ssl/keystore";
    private String keystorePassword = "OBF:RandomizedStringAsAPassword";
    private String keystoreManagerPassword = "OBF:RandomizedStringAsAPassword";
    private String truststorePath = "ssl/keystore";
    private String truststorePassword = "OBF:RandomizedStringAsAPassword";
 
 Accordingly, the keystore-file has to be located at ```ssl/keystore``` at the top level of the dev-catalogue repository.
 
 Upon ```start()```, the catalogue broker instantiates a Lehsan server object to provide the southbound interface towards the databases and to store the provided resource information from each connected database; and a http(s)-server that acts as the (additional) northbound interface as specified in the reThink Project.
 
Incoming (northbound) requests via http(s) on the ```/.well-known/*``` path as defined in the reThink resource path spec are handled via a servelet that invokes the ```rethinkRequestHandler = new RequestHandler(lwServer)```.  Note: The implementation of the RequestHandler class is contained in *RequestHandler.java*.

As the reThink resource path contains names, i.e. textual descripive tags, for each resource versus the core of the LWM2M-based catalogue employing unique numerical tags for efficiency, the ```RequestHandler``` generates a name:id map upon initialization for each supported resource.  The following code sniplet is given for hypertyResouces only.  A developer that intends to add additional catalogue object kinds to be supported by the reThink Catalogue would have to extend the code within the ```RequestHandler``` correspondingly.

    // name:id map for
    // populate hypertyResourceNameToID
    ObjectEnabler hypertyEnabler = new ObjectsInitializer(customModel).create(HYPERTY_MODEL_ID);
    HYPERTYMODEL = hypertyEnabler.getObjectModel().resources;
    // populate id:name map from resources
    for (Map.Entry<Integer, ResourceModel> entry : HYPERTYMODEL.entrySet()) {
      hypertyResourceNameToID.put(entry.getValue().name, entry.getKey());
    }
    LOG.debug("generated name:id map for hyperties: " + hypertyResourceNameToID);
    

The ```handleGet()``` method parses the received (resource) path and returns the corrsponding entries from the linkedHashMap, e.g. as shown here for the hyperty path:

String type = pathParts[0];
      
      switch (type) {
        case HYPERTY_TYPE_NAME: {
          return this.gson.toJson(hypertyNameToInstanceMap.keySet());
        }
        .....
        .....
        


 


#### reThink Catalogue Database

The implementation of the reThink Catalogue Database is concentrated in the catalogue database class which only depends on the Eclipse Leshan (client) class as seen in the figure below:

![developer_view_database](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-developer-view-database.png)


Upon initialization, the catalogue database checks if user-defined values for the (broker) host name and port number were given and if not, initializes them with default presets.

        if (serverHostName == null)
            serverHostName = DEFAULT_SERVER_HOSTNAME;

        if (serverPort == -1)
            serverPort = DEFAULT_SERVER_COAP_PORT;

        if (catObjsPath == null) {
            catObjsPath = "./catalogue_objects";
        }

As seen, by default, all catalogue objects (e.g. hyperties, protostubs, etc) that are to be stored in the database are all contained in the directory structure below ```./catalogue_objects``` as contained in the rethink/dev-catalgue github repository.

The database holds for each kind of catalogue object a list:

        RethinkInstance[] parsedHyperties;
        RethinkInstance[] parsedProtostubs;
        RethinkInstance[] parsedRuntimes;
        RethinkInstance[] parsedSchemas;
        RethinkInstance[] parsedSourcePackages;

If additional catalogue objects are to be supported by the catalogue, another ```RethinkInstance``` object has to be created.  In order to fill those objects, the databases parses all files below ```./catalogue_objects``` via the ```parseFiles()``` method:

        Map<Integer, RethinkInstance[]> resultMap = parseFiles(catObjsPath);

The ```parseFiles()``` method has internal knowledge about the supported *object kinds*:

        File hypertyFolder = new File(catObjsFolder, "hyperty");
        File stubFolder = new File(catObjsFolder, "protocolstub");
        File runtimeFolder = new File(catObjsFolder, "runtime");
        File schemaFolder = new File(catObjsFolder, "dataschema");
        
        LinkedList<RethinkInstance> hypertyInstances = new LinkedList<>();
        LinkedList<RethinkInstance> stubInstances = new LinkedList<>();
        LinkedList<RethinkInstance> runtimeInstances = new LinkedList<>();
        LinkedList<RethinkInstance> schemaInstances = new LinkedList<>();

and needs to be extended for the database to support additional catalogue objects.  As all catalogue objects are internally treated by the reThink Catalogue Database as *resources*, a description of those custom resources has to be added to the list of default resources available in the Leshan Client that is later instantiated.  To make these custom resources available, the catalogue database reads a descrition of those custom resources and initilizes the list of supported resource objects

        InputStream modelStream = getClass().getResourceAsStream("/model.json");
        objectModels.addAll(ObjectLoader.loadJsonStream(modelStream));
        
        HashMap<Integer, ObjectModel> map = new HashMap<>();
        for (ObjectModel objectModel : objectModels) {
            map.put(objectModel.id, objectModel);
        }
        
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(map));
        
        initializer.setClassForObject(3, Device.class);
        List<ObjectEnabler> enablers = initializer.createMandatory();

Please note that the description of custom resource objects as contained int the model.json file has to be altered when new catalogue objects are to be supported by the database.  This also applies to the following resource / memory allocation for each catalogue object contained below the ```./catalogue_objects``` path as shown in the following for hyperties stored in the reThink Catalgue Database:

        if (parsedHyperties.length > 0) {
            initializer.setInstancesForObject(HYPERTY_MODEL_ID, parsedHyperties);
            ObjectEnabler hypertyEnabler = initializer.create(HYPERTY_MODEL_ID);
            enablers.add(hypertyEnabler);

            // create idNameMap for hyperties
            LinkedHashMap<Integer, String> idNameMap = new LinkedHashMap<>();
            Map<Integer, ResourceModel> model = hypertyEnabler.getObjectModel().resources;

            // populate id:name map from resources
            for (Map.Entry<Integer, ResourceModel> entry : model.entrySet()) {
                idNameMap.put(entry.getKey(), entry.getValue().name);
            }

            // set id:name map on all hyperties
            for (RethinkInstance parsedHyperty : parsedHyperties) {
                parsedHyperty.setIdNameMap(idNameMap);
            }
        }

Finally, the Leshan Client is instantiated and told to attach to the reThink Catalogue Broker upon start.  

 final InetSocketAddress clientAddress = new InetSocketAddress("0", 0);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClient client = new LeshanClient(clientAddress, serverAddress, new ArrayList<LwM2mObjectEnabler>(
                enablers));

        // Start the client
        client.start();

        // Register to the server
        final String endpointIdentifier = UUID.randomUUID().toString();
        RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));
        if (response == null) {
            LOG.warn("Registration request timeout");
            return;
        }

To assure proper teardown of resources, the reThink Catalogue Database detaches from the Broker upon shut-down:

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


As the reThink Catalogue Database used the Leshan Client implementation without any modifications, a developer that whishes to extend the reThink Catalogue Database is unlikely change of the Leshan Client code.  In case, modification of Leshan are required, a developer is kindly asked to consult the [Leshan documentation](https://github.com/eclipse/leshan).  It should be noted though that at this point, the reThink Catalogue Database only uses Lehsan (in terms of linking against the unmodified version of it) which provides the basis to provide the reThink Cataluge under the given license terms.  Any modifications that propagate outside the scope of the rethink/dev-catalouge repository need to be evaluated agains potential impact on licensing.

