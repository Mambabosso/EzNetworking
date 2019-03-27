package eznetworking.client;

import java.io.Serializable;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import eznetworking.client.events.*;
import eznetworking.packet.Packet;
import eznetworking.util.Progress;
import eznetworking.util.Serializer;
import eznetworking.util.UniqueId;

public class Client {

    private final String id;

    private Socket client;
    private String host;
    private int port;

    private ArrayList<ErrorOccurred> errorOccurredEvents = new ArrayList<>();
    private ArrayList<ClientConnected> clientConnectedEvents = new ArrayList<>();
    private ArrayList<ClientDisconnected> clientDisconnectedEvents = new ArrayList<>();
    private ArrayList<NewDataAvailable> newDataAvailableEvents = new ArrayList<>();
    private ArrayList<BytesReceived> bytesReceivedEvents = new ArrayList<>();
    private ArrayList<PacketReceived> packetReceivedEvents = new ArrayList<>();
    private ArrayList<CustomReceived> customReceivedEvents = new ArrayList<>();
    private ArrayList<DataSendPrepared> dataSendPrepared = new ArrayList<>();

    private Object receiveLock = new Object();
    private Object sendLock = new Object();

    private int receiveBufferSize = 4096;
    private int sendBufferSize = 4096;

    private long bytesReceived;
    private long bytesSent;

    private Thread receiveThread;
    private boolean isReceiving;

    private Client() {
        this.id = UniqueId.generate();
    }

    public Client(String host, int port) {
        this();
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) {
            throw new IllegalArgumentException();
        }
        this.host = host;
        this.port = port;
    }

    public Client(Socket socket) {
        this();
        if (socket == null) {
            throw new IllegalArgumentException();
        }
        this.client = socket;
    }

    public synchronized boolean connect(boolean startReceiving) {
        try {
            if (client == null) {
                client = new Socket(host, port);
                client.setKeepAlive(true);
                client.setSoTimeout(500);
                triggerClientConnected();
                return startReceiving ? startReceiving() : true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    public boolean startReceiving() {
        try {
            if (receiveThread == null || !receiveThread.isAlive()) {
                isReceiving = true;
                receiveThread = new Thread(() -> {
                    try {
                        while (!receiveThread.isInterrupted() && isReceiving) {
                            byte[] bytes = receive(8, new Progress<Integer>());
                            if (bytes != null && bytes.length > 0) {
                                int type = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 0, 4)).getInt();
                                int length = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 4, 8)).getInt();
                                Progress<Integer> progress = new Progress<>();
                                triggerNewDataAvailable(type, length, progress);
                                triggerReceivedEvent(type, receive(length, progress));
                            } else if (bytes != null && bytes.length == 0) {
                            } else {
                                receiveThread.interrupt();
                            }
                        }
                    } catch (Exception ex) {
                        disconnect();
                    }
                });
                receiveThread.start();
                return true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    public synchronized boolean disconnect() {
        try {
            if (client != null) {
                client.close();
                client = null;
                triggerClientDisconnected();
                return true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    public boolean stopReceiving() {
        try {
            if (receiveThread != null && receiveThread.isAlive()) {
                isReceiving = false;
                receiveThread.interrupt();
                receiveThread.join();
                receiveThread = null;
                return true;
            }
            return false;
        } catch (Exception ex) {
            triggerErrorOccurred(ex);
            return false;
        }
    }

    private byte[] receive(int length, Progress<Integer> progress) {
        try {
            synchronized (receiveLock) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                byte[] buffer = (length > receiveBufferSize) ? new byte[receiveBufferSize] : new byte[length];
                int bytesRead = 0;
                for (int i = 0; i < length; i += bytesRead) {
                    bytesRead = client.getInputStream().read(buffer, 0, buffer.length);
                    byteBuffer.put(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                    progress.report(i);
                }
                progress.report(length);
                return byteBuffer.array();
            }
        } catch (SocketTimeoutException ex) {
            return new byte[0];
        } catch (Exception ex) {
            disconnect();
            return null;
        }
    }

    private boolean send(int type, byte[] data, Progress<Integer> progress) {
        try {
            synchronized (sendLock) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(8 + data.length).putInt(type).putInt(data.length).put(data);
                byte[] bytes = byteBuffer.array();
                triggerDataSendPrepared(type, bytes.length, progress);
                for (int offset = 0; offset < bytes.length; offset += sendBufferSize) {
                    int count = Math.min(sendBufferSize, bytes.length - offset);
                    client.getOutputStream().write(bytes, offset, count);
                    bytesSent += count;
                    progress.report(offset);
                }
                progress.report(bytes.length);
                return true;
            }
        } catch (Exception ex) {
            disconnect();
            return false;
        }
    }

    public boolean sendBytes(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException();
        }
        return send(1, data, new Progress<Integer>());
    }

    public boolean sendPacket(Packet packet) {
        if (packet == null) {
            throw new IllegalArgumentException();
        }
        return send(2, Serializer.serialize(packet), new Progress<Integer>());
    }

    public boolean sendCustom(int type, byte[] data) {
        if (type < 3 || data == null || data.length == 0) {
            throw new IllegalArgumentException();
        }
        return send(type, data, new Progress<Integer>());
    }

    public <T extends Serializable> boolean sendCustom(int type, T tClass) {
        if (type < 3 || tClass == null) {
            throw new IllegalArgumentException();
        }
        return send(type, Serializer.serialize(tClass), new Progress<Integer>());
    }

    // --- Events ---

    private void triggerReceivedEvent(int type, byte[] data) {
        if (type > 0 && data != null && data.length > 0) {
            switch (type) {
            case 1:
                triggerBytesReceived(data);
                break;
            case 2:
                triggerPacketReceived(Serializer.deserialize(data));
                break;
            default:
                triggerCustomReceived(type, data);
                break;
            }
        }
    }

    private void triggerErrorOccurred(Exception error) {
        for (ErrorOccurred eo : errorOccurredEvents) {
            eo.occurred(this, error);
        }
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

    private void triggerClientConnected() {
        for (ClientConnected cc : clientConnectedEvents) {
            cc.connected(this);
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

    private void triggerClientDisconnected() {
        for (ClientDisconnected cd : clientDisconnectedEvents) {
            cd.disconnected(this);
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

    private void triggerNewDataAvailable(int type, int length, Progress<Integer> progress) {
        for (NewDataAvailable nda : newDataAvailableEvents) {
            nda.available(this, type, length, progress);
        }
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

    private void triggerBytesReceived(byte[] data) {
        for (BytesReceived br : bytesReceivedEvents) {
            br.received(this, data);
        }
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

    private void triggerPacketReceived(Packet packet) {
        for (PacketReceived pr : packetReceivedEvents) {
            pr.received(this, packet);
        }
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
        PacketReceived result = (s, p) -> {
            if (p.getHeader().contentEquals(header) || header.contentEquals("*")) {
                listener.received(s, p);
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

    private void triggerCustomReceived(int type, byte[] data) {
        for (CustomReceived cr : customReceivedEvents) {
            cr.received(this, type, data);
        }
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
        CustomReceived result = (s, t, d) -> {
            if (t == type || type == 0) {
                listener.received(s, t, d);
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

    private void triggerDataSendPrepared(int type, int length, Progress<Integer> progress) {
        for (DataSendPrepared dsp : dataSendPrepared) {
            dsp.prepared(this, type, length, progress);
        }
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

    public Socket getSocket() {
        return client;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        if (receiveBufferSize < 8 || receiveBufferSize > 65536) {
            throw new IllegalArgumentException();
        }
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        if (sendBufferSize < 8 || sendBufferSize > 65536) {
            throw new IllegalArgumentException();
        }
        this.sendBufferSize = sendBufferSize;
    }

    public void setBufferSize(int bufferSize) {
        if (bufferSize < 8 || bufferSize > 65536) {
            throw new IllegalArgumentException();
        }
        this.receiveBufferSize = bufferSize;
        this.sendBufferSize = bufferSize;
    }

    public boolean isReceiving() {
        return (receiveThread != null && receiveThread.isAlive() && isReceiving);
    }
}
