package eznetworking.server;

import java.net.Socket;

import eznetworking.client.Client;

public class Connection extends Client {

    private Server parentServer;

    public Connection(Socket socket, Server parentServer) {
        super(socket);
        this.parentServer = parentServer;
    }

    @Override
    public synchronized boolean connect(boolean startReceiving) {
        throw new UnsupportedOperationException();
    }

    public Server getParentServer() {
        return parentServer;
    }
}
