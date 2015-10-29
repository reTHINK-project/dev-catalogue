# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.


For the broker and database to work properly, you have to to set an environment variable called "MODELS_FOLDER" that points to the "model" folder inside the source directory.
E.g.: "export MODELS_FOLDER=.../dev-catalogue/model" (not needed for compiling the jars)

The hyperties themselves are defined in the file "hyperties.json", located inside the resources folder of the reTHINK catalogue database. 
