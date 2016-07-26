package eu.rethink.catalogue.database.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by Robert Ende on 26.07.16.
 */
public class DatabaseConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DEFAULT_FILENAME = "dbconf.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String
            brokerHost = "localhost",
            coapHost = "localhost",
            endpoint = "DB_" + new Random().nextInt(Integer.MAX_VALUE),
            catalogueObjectsPath = "catalogue_objects";

    public int
            brokerPort = 5683,
            coapPort = 0,
            coapsPort = 0,
            lifeTime = 60,
            logLevel = 3;

    public static DatabaseConfig fromFile(String file) {
        File f = new File(file);
        DatabaseConfig config = null;

        if (!f.exists()) {
            LOG.info("File '{}' doesn't exist.", file);
        } else if (f.isDirectory()) {
            LOG.warn("File '{}' is a directory!", file);
        } else {
            try {
                config = gson.fromJson(new FileReader(f), DatabaseConfig.class);
                if (config == null) {
                    LOG.warn("Unable to create DatabaseConfig from '{}': File is empty or malformed.", file);
                }
            } catch (JsonSyntaxException e) {
                LOG.warn("Unable to create DatabaseConfig from '" + file + "': File is malformed.", e);
            } catch (FileNotFoundException e) {
                LOG.warn("File exists, but still FileNotFoundException occurred:", e);
                //e.printStackTrace();
            }
        }

        if (config == null) {
            LOG.info("Loading default Configuration...");
            config = new DatabaseConfig();
        }

        return config;
    }

    public static DatabaseConfig fromFile() {
        return fromFile(DEFAULT_FILENAME);
    }

    public void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            try {
                switch (args[i].toLowerCase()) {
                    case "-h":
                    case "-host":
                        brokerHost = args[++i];
                        break;
                    case "-usehttp":
                        LOG.warn("-usehttp is deprecated and will be ignored! Use the option 'sourcePackageURLProtocol' for the broker instead");
                        break;
                    case "-p":
                    case "-port":
                        brokerPort = Integer.parseInt(args[++i]);
                        i++;
                        break;
                    case "-o":
                    case "-op":
                    case "-objpath":
                        catalogueObjectsPath = args[++i];
                        break;
                    case "-d":
                    case "-domain":
                        LOG.warn("-domain is deprecated and will be ignored! Catalogue Broker modifies sourcePackageURL on-the-fly!");
                        break;
                    case "-lifetime":
                    case "-t":
                        lifeTime = Integer.parseInt(args[++i]);
                        break;
                    case "-endpoint":
                    case "-e":
                        endpoint = args[++i];
                        break;
                    case "-coapaddress":
                    case "-ca":
                        LOG.warn("-coapaddress is deprecated and will be ignored! Use -coaphost and -coapport instead.");
                        break;
                    case "-coaphost":
                    case "-ch":
                        coapHost = args[++i];
                        break;
                    case "-coapport":
                    case "-cp":
                        coapPort = Integer.parseInt(args[++i]);
                        break;
                    case "-coapsaddress":
                    case "-csa":
                        LOG.warn("-coapsaddress is deprecated and will be ignored! Use -coaphost and -coapsport instead.");
                        break;
                    case "-coapshost":
                    case "-csh":
                        LOG.warn("-coapshost is deprecated and will be ignored! Use -coaphost instead.");
                        break;
                    case "-coapsport":
                    case "-csp":
                        coapsPort = Integer.parseInt(args[++i]);
                        break;
                    case "-v":
                        // increase log level
                        logLevel = 2;
                        break;
                    case "-vv":
                        // increase log level
                        logLevel = 1;
                        break;
                    case "-vvv":
                        // increase log level
                        logLevel = 0;
                        break;
                    default:
                        if (args[i].startsWith("-")) { // unknown option -someOption
                            LOG.warn("Unknown option: {}", args[i]);
                        } else {
                            // ignore
                        }
                        break;
                }
            } catch (Exception e) {
                LOG.warn("Unable to parse option " + args[i] + " from " + Arrays.toString(args) + ":", e);
            }
        }
    }

    @Override
    public String toString() {
        return gson.toJson(this);
    }
}
