### User view

Looking at the reThink Catalogue from a user's perspective, one has to distinguish if a user aims at *retrieving* an object from the catalogue or at *populating* the catalogue with entries.

When **retrieving an object from the catalogue**, the catalogue appears to users as a single, monolytic implementation.  It is accessible via ports 80 (http) and 443 (https) in order to retrieve catalgue objects using the http(s)-get primitive.  As of version 2.1, the reThink Catalogue supports storing the following object types:
* protocolstub
* hyperty
* dataschema
* runtime
* idp-proxy

To retrieve objects from the reThink Catalogue, a client (e.g. a hyperty runtime environment) can access the object via the *object's resource path* that is expressed via RBNF [1] as follows: 

    <resource-path>  ::=  <hostname> "/.well-known/" <resource-type> "/" <resource-type-id>

    <hostname>  ::= "localhost"  |  <csp-domain>
    <csp-domain>  ::=   [ <url-string> "." ]  <url-string> "." <top-level-domain>
    <top-level-domain>  ::=  "de" | "com" | "org" | "fr" | "eu"
    <resource-type> ::= "protocolstub"  |  "hyperty" | "runtime"  |  "dataschema"
    <resource-type-id>  ::= "default"  | <identifier>
    <identifier>  ::= <url-string>
    <url-string> ::= <url-char> ...
    <url-char>  ::= <lower-case-char> | <upper-case-char> | <digit> | "_" | "-"
    <lower-case-char> ::= "a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" 
                                | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "s" 
                                | "t" | "u" | "v" | "w" | "x" | "y" | "z"
    <upper-case-char> ::= "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H" | "I" | "J" 
                                | "K" | "L" | "M" | "N" | "O" | "P" | "Q" | "R" | "S" 
                                | "T" | "U" | "V" | "W" | "X" | "Y" | "Z"
    <digit>  ::=   "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" 

Note: The most current verion of the resource data path definiton can be found  [here](https://github.com/reTHINK-project/architecture/blob/master/docs/interface-design/resource-path.md).

The following examples show the resource path for two protocol stubs, namely *myAwesome_protocol-stub9* and *myAwesome_protocol-stub9*, which are stored in a reThink Catalogue hosted on the host *catalogue.rethink.eu*.  Note that it is also possible to use *localhost* in case a catalogue is locally deployed on the system that originates the request.

    catalogue.rethink.eu/.well-known/protocolstub/myAwesome_protocol-stub9
    localhost/.well-known/protocolstub/myAwesome_protocol-stub9

For **retrieving a list of all available objects of a given type**, the user can simply contact the catalogue using a shortened version of the resource path, i.e. not including  < resource-type-id >  in it.

In order to **populate the catalogue with data**, the user sees the catalogue as distributed system consisting of one reThink Catalogue Broker and at least one reThink Catalogue Database.  Every reThink Catalogue Database can populate entries at the broker by reading sets of catalogue objects from the filesystem and forwarding information about them to the broker.

At the database, catalogue objects are hirarchically stored in the file system.  Those resources are stored in the *catalogue_objects* directory which exists in parallel to the *catalogue_database* directory that holds the runtime of the reThink Catalogue Database.  Beneath *catalogue_objects* exist subdirectories, one for each *type of cataogue objects*, which in turn hold one subdirectory for each catalogue object. Please note that it is not allowed to name an object folder and therefore a catalogue object "default". To set a default object for a given type, the "-default" option for the Catalogue Broker has to be used instead.

The following figure illustrates the directory structure; two hyperty catalogue objects, named *FirstHyperty* and *SecondHyperty* are included.

    --|----- catalogue_database
      |
      |--+-- catalogue_objects
         |
         |--+-- hyperty
         |  |
         |  |--+-- FirstHyperty
         |  |  |
         |  |  |----- description.json
         |  |  |----- sourcePackage.json
         |  |  |----- sourceCode.js
         |  |
         |  |--+-- SecondHyperty
         |  |  |----- ...
         |  |
         |--+-- protostub
         |  |----- ...
         |
         |--+-- dataschema
         |  |----- ...
         |
         |--+-- runtime
            |----- ...
         |
         |--+-- idpproxy
            |----- ...

Each catalogue object's directory contains up to three files that contain the descption of the catalogue object.  *description.json* is mandatory and contains the full description of the object.  If the *sourcePackage* field in *description.json* is not initialized, i.e. left blank, the contents of the (optional) *sourcePackage.json* file is used to initialize the *sourcePackage* field.  If *sourcePackage.json* does not contain an initialized "sourceCode" field, the contents of *sourceCode.** are used to set the *sourceCode* field.  Note that the file *sourceCode* may end in either of prefixes *.js* or *.json* as, depending on the type of catalogue object, the *sourceCode* field may contain a JavaScript source code, or a JSON-encoded dataschema.

The catalogue object descriptions below the *catalogue_objects* directory are read-in once while starting the reThink Catalogue Database program.

The following **Figure illustrates the two users' views on the reThink Catalogue**, showing the one monolytic appearance that can be accessed via http and https to retrieve objects fromt the catalogue, and the distributed internal view to populate the catalogue, here by having two instances of the reThink Catalogue Database.

![external_view](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-external-view.png)

In order to learn **how to start either of the catalogue database or cataluge broker** from a deployable docker image or by checking out the sourcode from GitHub, please go to the [Getting Started](../getting_started) section of this documentation.


**References**
[1] [Routing Backus-Naur Form (RBNF): A Syntax Used to Form Encoding Rules in Various Routing Protocol Specifications](http://tools.ietf.org/html/rfc5511)
