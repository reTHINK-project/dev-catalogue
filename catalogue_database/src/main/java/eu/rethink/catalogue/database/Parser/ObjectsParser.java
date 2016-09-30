/*
 * Copyright [2015-2017] Fraunhofer Gesellschaft e.V., Institute for
 * Open Communication Systems (FOKUS)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.rethink.catalogue.database.Parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import eu.rethink.catalogue.database.CatalogueObjectInstance;
import eu.rethink.catalogue.database.exception.CatalogueObjectParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static eu.rethink.catalogue.database.Utils.*;

/**
 * Created by Robert Ende on 29.09.16.
 */
public class ObjectsParser {
    private static final Logger LOG = LoggerFactory.getLogger(ObjectsParser.class);

    // directory filter used in CatalogueObjectInstance class
    private static final FileFilter dirFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory();
        }
    };

    private File baseDir;

    public ObjectsParser(File baseDir) {
        this.baseDir = baseDir;
    }

    public ObjectsParser(String baseDirPath) {
        this.baseDir = new File(baseDirPath);
    }

    /**
     * Parses the Catalogue Objects folder
     *
     * @return modelID:CatalogueObjectInstances map
     * @throws CatalogueObjectParsingException - thrown on parsing error
     */
    public Map<Integer, Set<CatalogueObjectInstance>> parse() throws CatalogueObjectParsingException {

        if (!baseDir.exists()) {
            throw new CatalogueObjectParsingException("Catalogue Objects folder '" + baseDir.getAbsolutePath() + "' does not exist!");
        } else if (!baseDir.isDirectory()) {
            throw new CatalogueObjectParsingException("Catalogue Objects folder '" + baseDir.getAbsolutePath() + "' is not a directory!");
        } else {
            LOG.debug("Parsing '{}'", baseDir.getPath());

            File[] typeFolders = baseDir.listFiles(dirFilter);
            Map<Integer, Set<CatalogueObjectInstance>> modelObjectsMap = new LinkedHashMap<>(MODEL_IDS.size());

            // setup modelObjectsMap
            for (Integer modelId : MODEL_IDS) {
                modelObjectsMap.put(modelId, new LinkedHashSet<CatalogueObjectInstance>());
            }

            for (File typeFolder : typeFolders) {
                Integer modelId = MODEL_NAME_TO_ID_MAP.get(typeFolder.getName());

                if (modelId == null) {
                    LOG.warn("Unable to find matching model ID for folder '" + typeFolder.getPath() + "'");
                } else {
                    LOG.debug("Parsing '{}'", typeFolder.getPath());
                    File[] instanceFolders = typeFolder.listFiles(dirFilter);

                    for (File instanceFolder : instanceFolders) {
                        LOG.debug("Parsing '{}'", instanceFolder.getPath());
                        if (instanceFolder.getName().toLowerCase().equals("default")) {
                            LOG.warn("Defining default instance by calling it 'default' is not supported anymore. " +
                                    "Please use the Catalogue Broker option '-default' instead. " +
                                    "Folder " + instanceFolder.getPath() + " will be skipped...");
                            continue;
                        }
                        File desc = new File(instanceFolder, "description.json");
                        File pkg = new File(instanceFolder, "sourcePackage.json");
                        JsonObject jDesc = parseJson(desc);
                        // only continue if file was parsed successfully
                        if (jDesc != null) {
                            JsonObject jPkg = null;

                            // either parse sourcePackage from file or extract it from the description.json
                            if (pkg.exists()) {
                                //LOG.debug("Parsing " + pkg.getPath());
                                jPkg = parseJson(pkg);
                            } else if (jDesc.has("sourcePackage")) {
                                jPkg = jDesc.remove("sourcePackage").getAsJsonObject();
                            }

                            if (jPkg != null) {
                                // warn if parsed from file and sourcePackage also included in description.json
                                if (jDesc.has("sourcePackage")) {
                                    LOG.warn("Duplicate sourcePackage! Removing sourcePackage from {}", desc.getPath());
                                    jDesc.remove("sourcePackage");
                                }

                                // put objectName from descriptor into sourcePackage
                                String packageName = jDesc.get("objectName").getAsString() + "_sp";
                                jPkg.addProperty("objectName", packageName);

                                // put sourcePackageURL that references this sourcePackage into descriptor
                                jDesc.addProperty("sourcePackageURL", "/sourcepackage/" + packageName);

                                // check if there is a sourceCode file
                                File code = null;
                                for (File file : instanceFolder.listFiles()) {
                                    if (file.getName().startsWith("sourceCode")) {
                                        LOG.trace("Found sourceCode file in '{}'", instanceFolder.getPath());
                                        code = file;
                                        break;
                                    }
                                }

                                // finally create instance objects
                                CatalogueObjectInstance descriptor = new CatalogueObjectInstance(modelId, jDesc);
                                CatalogueObjectInstance sourcePackage = new CatalogueObjectInstance(SOURCEPACKAGE_MODEL_ID, jPkg, code);

                                if (descriptor.isValid() && sourcePackage.isValid()) {
                                    modelObjectsMap.get(modelId).add(descriptor);
                                    modelObjectsMap.get(SOURCEPACKAGE_MODEL_ID).add(sourcePackage);
                                } else {
                                    LOG.warn("Validation failed for descriptor or sourcePackage in '{}' and will be ignored", instanceFolder.getPath());
                                }
                            } else {
                                LOG.warn("No sourcePackage in instance folder '{}'", instanceFolder.getPath());
                            }

                        } else {
                            LOG.warn("No description in instance folder '{}'", instanceFolder.getPath());
                        }
                    }
                }
            }
            return modelObjectsMap;
        }
    }

    /**
     * Parse file and return it as a JSONObject
     *
     * @param f - file to be parsed
     * @return JsonObject based on the contents of the given file
     */
    private JsonObject parseJson(File f) {
        LOG.trace("Parsing '{}' to JSON", f.getPath());
        JsonObject json = null;
        try {
            json = parser.parse(new FileReader(f)).getAsJsonObject();
        } catch (FileNotFoundException e) {
            // should never happen
            LOG.warn("'{}' does not exist!", f.getPath());
        } catch (JsonParseException | IllegalStateException e) {
            LOG.warn("Parsing '" + f.getPath() + "' failed!", e);
            printBrokenFile(f);
        }
        return json;
    }

    /**
     * Print file contents in case of parsing error
     *
     * @param f - file to be logged
     */
    private void printBrokenFile(File f) {
        LOG.warn("Contents of file " + f.getAbsolutePath() + ":");
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    LOG.error(line);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
            LOG.warn("Error while printing file contents:", e);
        }
    }
}
