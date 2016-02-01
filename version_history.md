# version history
## 2.0
### user view
* changed how catalogue objects are defined, see catalogue_objects for the new file & folder structure
* https support
* additional launch options for catalogue broker (set ssl port, set keystore etc)
* support for hyperty runtimes and data schemas
* broker runs on port 80/443 by default
* define default hyperty/protostub etc. by naming it "default"

### internal view
* moved all sourcePackages to subpath .../sourcepackage
* generate field "objectName" for sourcePackages (based on catalogue object it belongs to) for easy mapping in broker
* generate sourcePackageURL based on broker address given to catalogue database on launch
* added version field to catalogue objects
* use self-signed certificate for ssl
* sourceCode can be defined in separate file

## 2.1
### user view
* changed how Catalogue Database is started, using optional launch options (see README.md for details)
* Catalogue Database can be started without providing arguments (will try to connect to localhost:5683 and using "catalogue_objects" folder from current working directory.
* the sourceCode file that can contain the source code of a catalogue object can now have any file extension (name has to be "sourceCode.*")


### internal view
* sourceCode file can have any file extension (as mentioned in user view)
* Catalogue Database has default values for Broker hostname, port and location of catalogue objects folder, making it usable without providing launch arguments.