package config;

public class SmartPoolWalletConfig {
    private String walletMneumonic;
    private String walletPass;

    public SmartPoolWalletConfig(String name, String pass){
        walletMneumonic = name;
        walletPass = pass;
    }

    public String getWalletMneumonic(){
        return walletMneumonic;
    }

    public String getWalletPass() {
        return walletPass;
    }
}
