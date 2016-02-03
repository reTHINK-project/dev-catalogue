### User view

Looking at the reThink Catalogue from a user's perspective, one has to distinguish if a user aims at *retrieving* an object from the catalogue or at *populating* the catalogue with entries.

When **retrieving an object from the catalogue**, the catalogue appears to users as a single, monolytic implementation.  It is accessible via ports 80 (http) and 443 (https) in order to retrieve catalgue objects using the http(s)-get primitive.  As of version 2.1, the reThink Catalogue supports storing the following objects:
* protocolstub
* hyperty
* dataschema
* runtime

To retrieve objects from the reThink Catalogue, a client (e.g. a hyperty runtime environment) can access the object via the *object's resource path* that is expressed via RBNF [1] as follows: 

    <resource-path>  ::=  <hostname> "/.well-known/" <resource-type> "/" <resource-type-id>

    <hostname>  ::= "localhost"  |  <csp-domain>
    <csp-domain>  ::=   [ <url-string> "." ]  <url-string> "." <top-level-domain>
    <top-level-domain>  ::=  "de" | "com" | "org" | "fr" | "eu"
    <resource-type> ::= "protocolstub"  |  "hyperty" 
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

**Note:  the BNFR spec of the resource path needs to be updated, as the [most current version](https://github.com/reTHINK-project/architecture/blob/master/docs/interface-design/resource-path.md) does not reflect all possible objects that can be stored in the catalogue**

The following examples show the resource path for two protocol stubs, namely *myAwesome_protocol-stub9* and *myAwesome_protocol-stub9*, which are stored in a reThink Catalogue hosted on the host *catalogue.rethink.eu*.  Note that it is also possible to use *localhost* in case a catalogue is locally deployed on the system that originates the request.

    catalogue.rethink.eu/.well-known/protocolstub/myAwesome_protocol-stub9
    localhost/.well-known/protocolstub/myAwesome_protocol-stub9

For **retrieving a list of all available objects of a given kind**, the user can simply contact the catalogue using a shortened version of the resource path, i.e. not including  < resource-type-id >  in it.

In order to **populate the catalogue with data**, the user sees the catalogue as distributed system consisting of one reThink Catalogue Broker and at least one reThink Catalogue Database.  Every reThink Catalogue Database can populate entries at the broker by reading sets of catalogue objects from the filesystem and forwarding information about them to the broker.

At the database, catalogue objects are hirarchically stored in the file system.  Those resources are stored in the *catalogue_objects* directory which exists in parallel to the *catalogue_database* directory that holds the runtime of the reThink Catalogue Database.  Beneath *catalogue_objects* exist subdirectories, one for each *kind of cataogue objects*, which in turn hold one subdirectory for each catalogue object.

    --|----- catalogue_database
      |--+-- catalogue_objects
         |--+-- hyperty
            |--+-- FirstHyperty
               |----- description.json
               |----- sourcePackage.json
               |----- sourceCode.js
            |--+-- SecondHyperty
               |----- ...
         |--+-- protostub
            |----- ...
         |--+-- dataschema
            |----- ...
         |--+-- runtime
            |----- ...

            |----- ...

**bla bla bla bla.  Need to finsih the text here**

The following **Figure illustrates the two users' views on the reThink Catalogue**, showing the one monolytic appearance that can be accessed via http and https to retrieve objects fromt the catalogue, and the distributed internal view to populate the catalogue, here by having two instances of the reThink Catalogue Database.

![external_view](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-external-view.png)


**References**
[1] [Routing Backus-Naur Form (RBNF): A Syntax Used to Form Encoding Rules in Various Routing Protocol Specifications](http://tools.ietf.org/html/rfc5511)
