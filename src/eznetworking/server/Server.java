package eznetworking.server;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Function;

import eznetworking.packet.Packet;
import eznetworking.server.events.*;
import eznetworking.util.Progress;
import eznetworking.util.Runner;
import eznetworking.util.UniqueId;

public class Server implements Iterable<Connection> {

    private final String id;

    private ServerSocket server;
    private int port;

    private ArrayList<ErrorOccurred> errorOccurredEvents = new ArrayList<>();
    private ArrayList<ServerStarted> serverStartedEvents = new ArrayList<>();
    private ArrayList<ServerStopped> serverStoppedEvents = new ArrayList<>();
    private ArrayList<ClientConnected> clientConnectedEvents = new ArrayList<>();
    private ArrayList<ClientDisconnected> clientDisconnectedEvents = new ArrayList<>();
    private ArrayList<NewDataAvailable> newDataAvailableEvents = new ArrayList<>();
    private ArrayList<BytesReceived> bytesReceivedEvents = new ArrayList<>();
    private ArrayList<PacketReceived> packetReceivedEvents = new ArrayList<>();
    private ArrayList<CustomReceived> customReceivedEvents = new ArrayList<>();
    private ArrayList<DataSendPrepared> dataSendPrepared = new ArrayList<>();

    private HashMap<String, Connection> clients = new HashMap<>();

    private Thread listenThread;
    private boolean isListening;

    private ArrayList<String> blacklistedIPAddresses = new ArrayList<>();
    private Function<Connection, Boolean> clientFilter = (c) -> true;

    private Server() {
        this.id = UniqueId.generate();
    }

    public Server(int port) {
        this();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException();
        }
        this.port = port;
    }

    public synchronized boolean start() {
        try {
            if (server == null && listenThread == null) {
                isListening = true;
                server = new ServerSocket(port);
                listenThread = new Thread(() -> {
                    while (!listenThread.isInterrupted() && isListening) {
                        try {
                            Socket acceptedSocket = server.accept();
                            Thread t = new Thread(() -> {
                                try {
                                    final Socket socket = acceptedSocket;
                                    InetAddress address = socket.getInetAddress();
                                    if (!blacklistedIPAddresses.contains(address.getHostAddress())) {
                                        Connection client = new Connection(socket, this);
                                        if (clientFilter.apply(client) && initClient(client)) {
                                            triggerClientConnected(client);
                                        } else {
                                            client.disconnect();
                                        }
                                    } else {
                                        socket.close();
                                    }
                                } catch (Exception ex) {
                                }
                            });
                            t.start();
                        } catch (Exception ex) {
                        }
                    }
                });
                listenThread.start();
                triggerServerStarted();
                return true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    public synchronized boolean stop() {
        try {
            if (server != null && listenThread != null) {
                isListening = false;
                server.close();
                listenThread.interrupt();
                listenThread.join();
                server = null;
                listenThread = null;
                triggerServerStopped();
                return true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    public boolean blacklistClient(Connection c, boolean disconnect) {
        if (c == null) {
            throw new IllegalArgumentException();
        }
        String ip = c.getSocket().getInetAddress().getHostAddress();
        return disconnect ? blacklistedIPAddresses.add(ip) && c.disconnect() : blacklistedIPAddresses.add(ip);
    }

    public boolean[] disconnectClients(Connection[] c) {
        if (c == null) {
            throw new IllegalArgumentException();
        }
        boolean[] result = new boolean[c.length];
        for (int i = 0; i < c.length; i++) {
            result[i] = c[i].disconnect();
        }
        return result;
    }

    @Override
    public Iterator<Connection> iterator() {
        return Arrays.asList(getClients()).iterator();
    }

    private boolean initClient(Connection client) {
        try {
            client.getSocket().setKeepAlive(true);
            client.getSocket().setSoTimeout(500);
            client.addClientConnectedListener((s) -> {
                throw new UnsupportedOperationException();
            });
            client.addClientDisconnectedListener((s) -> {
                triggerClientDisconnected(client);
            });
            client.addNewDataAvailableListener((s, t, l, p) -> {
                triggerNewDataAvailable(client, t, l, p);
            });
            client.addBytesReceivedListener((s, d) -> {
                triggerBytesReceived(client, d);
            });
            client.addPacketReceivedListener((s, p) -> {
                triggerPacketReceived(client, p);
            });
            client.addCustomReceivedListener((s, t, d) -> {
                triggerCustomReceived(client, t, d);
            });
            client.addDataSendPreparedListener((s, t, l, p) -> {
                triggerDataSendPrepared(client, t, l, p);
            });
            return client.startReceiving();
        } catch (Exception ex) {
            return false;
        }
    }

    // --- Events ---

    private void triggerErrorOccurred(Exception error) {
        Runner.run(() -> {
            for (ErrorOccurred eo : errorOccurredEvents) {
                eo.occurred(this, error);
            }
        });

    }

    public void addErrorOccurredListener(ErrorOccurred listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        errorOccurredEvents.add(listener);
    }

    public boolean removeErrorOccurredListener(ErrorOccurred listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return errorOccurredEvents.remove(listener);
    }

    private void triggerServerStarted() {
        Runner.run(() -> {
            for (ServerStarted ss : serverStartedEvents) {
                ss.started(this);
            }
        });
    }

    public void addServerStartedListener(ServerStarted listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        serverStartedEvents.add(listener);
    }

    public boolean removeServerStartedListener(ServerStarted listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return serverStartedEvents.remove(listener);
    }

    private void triggerServerStopped() {
        Runner.run(() -> {
            for (ServerStopped ss : serverStoppedEvents) {
                ss.stopped(this);
            }
        });
    }

    public void addServerStoppedListener(ServerStopped listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        serverStoppedEvents.add(listener);
    }

    public boolean removeServerStoppedListener(ServerStopped listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return serverStoppedEvents.remove(listener);
    }

    private void triggerClientConnected(Connection client) {
        if (clients.put(client.getId(), client) == null) {
            Runner.run(() -> {
                for (ClientConnected cc : clientConnectedEvents) {
                    cc.connected(this, client);
                }
            });
        }
    }

    public void addClientConnectedListener(ClientConnected listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        clientConnectedEvents.add(listener);
    }

    public boolean removeClientConnectedListener(ClientConnected listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return clientConnectedEvents.remove(listener);
    }

    private void triggerClientDisconnected(Connection client) {
        if (clients.remove(client.getId(), client)) {
            Runner.run(() -> {
                for (ClientDisconnected cd : clientDisconnectedEvents) {
                    cd.disconnected(this, client);
                }
            });
        }
    }

    public void addClientDisconnectedListener(ClientDisconnected listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        clientDisconnectedEvents.add(listener);
    }

    public boolean removeClientDisconnectedListener(ClientDisconnected listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return clientDisconnectedEvents.remove(listener);
    }

    private void triggerNewDataAvailable(Connection client, int type, int length, Progress<Integer> progress) {
        Runner.run(() -> {
            for (NewDataAvailable nda : newDataAvailableEvents) {
                nda.available(this, client, type, length, progress);
            }
        });
    }

    public void addNewDataAvailableListener(NewDataAvailable listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        newDataAvailableEvents.add(listener);
    }

    public boolean removeNewDataAvailableListener(NewDataAvailable listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return newDataAvailableEvents.remove(listener);
    }

    private void triggerBytesReceived(Connection client, byte[] data) {
        Runner.run(() -> {
            for (BytesReceived br : bytesReceivedEvents) {
                br.received(this, client, data);
            }
        });
    }

    public void addBytesReceivedListener(BytesReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        bytesReceivedEvents.add(listener);
    }

    public boolean removeBytesReceivedListener(BytesReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return bytesReceivedEvents.remove(listener);
    }

    private void triggerPacketReceived(Connection client, Packet packet) {
        Runner.run(() -> {
            for (PacketReceived pr : packetReceivedEvents) {
                pr.received(this, client, packet);
            }
        });
    }

    public void addPacketReceivedListener(PacketReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        packetReceivedEvents.add(listener);
    }

    public PacketReceived addPacketReceivedListener(String header, PacketReceived listener) {
        if (header == null || header.trim().isEmpty() || listener == null) {
            throw new IllegalArgumentException();
        }
        PacketReceived result = (s, c, p) -> {
            if (p.getHeader().contentEquals(header) || header.contentEquals("*")) {
                listener.received(s, c, p);
            }
        };
        addPacketReceivedListener(result);
        return result;
    }

    public boolean removePacketReceivedListener(PacketReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return packetReceivedEvents.remove(listener);
    }

    private void triggerCustomReceived(Connection client, int type, byte[] data) {
        Runner.run(() -> {
            for (CustomReceived cr : customReceivedEvents) {
                cr.received(this, client, type, data);
            }
        });
    }

    public void addCustomReceivedListener(CustomReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        customReceivedEvents.add(listener);
    }

    public CustomReceived addCustomReceivedListener(int type, CustomReceived listener) {
        if (type < 0 || type == 1 || type == 2 || listener == null) {
            throw new IllegalArgumentException();
        }
        CustomReceived result = (s, c, t, d) -> {
            if (t == type || type == 0) {
                listener.received(s, c, t, d);
            }
        };
        addCustomReceivedListener(result);
        return result;
    }

    public boolean removeCustomReceivedListener(CustomReceived listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return customReceivedEvents.remove(listener);
    }

    private void triggerDataSendPrepared(Connection client, int type, int length, Progress<Integer> progress) {
        Runner.run(() -> {
            for (DataSendPrepared dsp : dataSendPrepared) {
                dsp.prepared(this, client, type, length, progress);
            }
        });
    }

    public void addDataSendPreparedListener(DataSendPrepared listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        dataSendPrepared.add(listener);
    }

    public boolean removeDataSendPreparedListener(DataSendPrepared listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return dataSendPrepared.remove(listener);
    }

    // ---

    public String getId() {
        return id;
    }

    public ServerSocket getServerSocket() {
        return server;
    }

    public Connection getClient(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException();
        }
        return clients.get(id);
    }

    public String[] getClientIds() {
        return clients.keySet().toArray(new String[clients.size()]);
    }

    public Connection[] getClients() {
        return clients.values().toArray(new Connection[clients.size()]);
    }

    public ArrayList<String> getBlacklistedIPAddresses() {
        return blacklistedIPAddresses;
    }

    public Function<Connection, Boolean> getClientFilter() {
        return clientFilter;
    }

    public void setClientFilter(Function<Connection, Boolean> filter) {
        if (filter != null) {
            clientFilter = filter;
        }
    }

    public boolean isListening() {
        return (listenThread != null && listenThread.isAlive() && isListening);
    }
}
