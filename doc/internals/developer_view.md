### Developer view
* specifications, interfaces
* including usable tools, tutorials and working examples
* 


As described in the [user view](./user_view.md) section, the reThink Catalogue consists of two functional components: the reThink Catalogue Broker and the reThink Catalogue Database.  The developer view describes per component the latter's functional block in the implementation.  Focus is herein given to those aspects of the implementation that need to be changed if a future revision of the reThink Catalogue is extended towards:
  * supporting additional catalgoue object kinds
  * tbf.


#### reThink Catalogue Broker




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

