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