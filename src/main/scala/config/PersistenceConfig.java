package config;

public class PersistenceConfig {
    private String host;
    private int port;
    private String database;
    private boolean ssl;

    private String username;
    private String password;

    public PersistenceConfig(String hostAddress, int portNum, String dbName, boolean sslOn){
        host = hostAddress;
        port = portNum;
        database = dbName;
        ssl = sslOn;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public boolean isSslConnection() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

}
