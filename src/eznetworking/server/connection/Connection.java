package eznetworking.server.connection;

import java.net.Socket;

import eznetworking.client.Client;
import eznetworking.server.Server;

public class Connection extends Client {

    private Server parentServer;
    private int group;
    private PowerLevel powerLevel;

    public Connection(Socket socket, Server parentServer) {
        super(socket);
        this.parentServer = parentServer;
        this.group = 1;
        this.powerLevel = PowerLevel.LOW;
    }

    @Override
    public synchronized boolean connect(boolean startReceiving) {
        throw new UnsupportedOperationException();
    }

    public Server getParentServer() {
        return parentServer;
    }

    public int getGroup() {
        return group;
    }

    public void setGroup(int group) {
        this.group = group;
    }

    public PowerLevel getPowerLevel() {
        return powerLevel;
    }

    public void setPowerLevel(PowerLevel powerLevel) {
        if (powerLevel == PowerLevel.ADMINISTRATOR) {
            for (Connection c : parentServer) {
                if (c.getPowerLevel() == PowerLevel.ADMINISTRATOR) {
                    throw new SecurityException();
                }
            }
        }
        this.powerLevel = powerLevel;
    }
}
