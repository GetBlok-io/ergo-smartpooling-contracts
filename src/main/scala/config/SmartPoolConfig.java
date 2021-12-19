package config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.node.SmartPoolNodeConfig;
import config.params.SmartPoolParameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Paths;

/**
 * Based off ErgoToolConfig
 * */
public class SmartPoolConfig {
    private SmartPoolNodeConfig node;
    private SmartPoolParameters parameters;
    private PersistenceConfig persistence;
    private LoggingConfig logging;

    public SmartPoolConfig(SmartPoolNodeConfig nodeConf, SmartPoolParameters paramConf, PersistenceConfig persConfig, LoggingConfig logConf){
        node = nodeConf;
        parameters = paramConf;
        persistence = persConfig;
        logging = logConf;
    }
    /**
     * Returns Ergo node configuration
     */
    public SmartPoolNodeConfig getNode() {
        return node;
    }

    /**
     * Modified configuration for list
     *
     */
    public SmartPoolParameters getParameters() {
        return parameters;
    }

    public void setParameters(SmartPoolParameters parameters) {
        this.parameters = parameters;
    }

    public void setNode(SmartPoolNodeConfig node) {
        this.node = node;
    }

    public PersistenceConfig getPersistence() {
        return persistence;
    }

    public void setPersistence(PersistenceConfig persistence) {
        this.persistence = persistence;
    }

    /**
     * Load config from the given reader.
     *
     * @param reader reader of the config json.
     * @return ErgoToolConfig created form the file content.
     */
    public static SmartPoolConfig load(Reader reader) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(reader, SmartPoolConfig.class);
    }

    /**
     * Load config from the given file.
     *
     * @param file file with config json.
     * @return ErgoToolConfig created form the file content.
     */
    public static SmartPoolConfig load(File file) throws FileNotFoundException {
        Gson gson = new GsonBuilder().create();
        FileReader reader = new FileReader(file);
        return gson.fromJson(reader, SmartPoolConfig.class);
    }

    /**
     * Load config from the given file.
     *
     * @param fileName name of the file relative to the current directory.
     *                 The file is resolved using {@link File#getAbsolutePath()}.
     * @return ErgoToolConfig created form the file content.
     */
    public static SmartPoolConfig load(String fileName) throws FileNotFoundException {
        File file = Paths.get(fileName).toAbsolutePath().toFile();
        return load(file);
    }

    public SmartPoolConfig copy() {
        return new SmartPoolConfig(node, parameters.copy(), persistence, logging);
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }
}
