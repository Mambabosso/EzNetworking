package eznetworking.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

import eznetworking.client.events.*;
import eznetworking.packet.Packet;
import eznetworking.util.Progress;
import eznetworking.util.Runner;
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
    private ArrayList<DataAvailable> dataAvailableEvents = new ArrayList<>();
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

    public synchronized boolean connect() {
        return connect(false);
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
                                triggerDataAvailable(type, length, progress);
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
                progress.started(0);
                InputStream inputStream = client.getInputStream();
                int bytesRead = 0;
                for (int i = 0; i < length; i += bytesRead) {
                    bytesRead = inputStream.read(buffer, 0, buffer.length);
                    byteBuffer.put(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                    progress.changed(i);
                }
                progress.finished(length);
                return byteBuffer.array();
            }
        } catch (BufferOverflowException | SocketTimeoutException ex) {
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
                progress.started(0);
                OutputStream outputStream = client.getOutputStream();
                for (int i = 0; i < bytes.length; i += sendBufferSize) {
                    int count = Math.min(sendBufferSize, bytes.length - i);
                    outputStream.write(bytes, i, count);
                    bytesSent += count;
                    progress.changed(i);
                }
                progress.finished(bytes.length);
                return true;
            }
        } catch (BufferOverflowException ex) {
            return false;
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
            if (type == 1) {
                triggerBytesReceived(data);
            } else if (type == 2) {
                triggerPacketReceived(Serializer.deserialize(data));
            } else {
                triggerCustomReceived(type, data);
            }
        }
    }

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

    private void triggerClientConnected() {
        Runner.run(() -> {
            for (ClientConnected cc : clientConnectedEvents) {
                cc.connected(this);
            }
        });
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
        Runner.run(() -> {
            for (ClientDisconnected cd : clientDisconnectedEvents) {
                cd.disconnected(this);
            }
        });
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

    private void triggerDataAvailable(int type, int length, Progress<Integer> progress) {
        Runner.run(() -> {
            for (DataAvailable nda : dataAvailableEvents) {
                nda.available(this, type, length, progress);
            }
        });
    }

    public void addDataAvailableListener(DataAvailable listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        dataAvailableEvents.add(listener);
    }

    public boolean removeDataAvailableListener(DataAvailable listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return dataAvailableEvents.remove(listener);
    }

    private void triggerBytesReceived(byte[] data) {
        Runner.run(() -> {
            for (BytesReceived br : bytesReceivedEvents) {
                br.received(this, data);
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

    private void triggerPacketReceived(Packet packet) {
        Runner.run(() -> {
            for (PacketReceived pr : packetReceivedEvents) {
                pr.received(this, packet);
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
        PacketReceived result = (s, p) -> {
            if (p.getHeader().contentEquals(header) || header.contentEquals("*")) {
                listener.received(s, p);
            }
        };
        addPacketReceivedListener(result);
        return result;
    }

    public <T> PacketReceived addPacketReceivedListener(String header, Class<T> tClass, Consumer<T> listener) {
        if (header == null || header.trim().isEmpty() || tClass == null || listener == null) {
            throw new IllegalArgumentException();
        }
        PacketReceived result = (s, p) -> {
            if ((p.getHeader().contentEquals(header) || header.contentEquals("*")) && p.getPayloadClass().equals(tClass)) {
                listener.accept(p.unpack(tClass));
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
        Runner.run(() -> {
            for (CustomReceived cr : customReceivedEvents) {
                cr.received(this, type, data);
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
        Runner.run(() -> {
            for (DataSendPrepared dsp : dataSendPrepared) {
                dsp.prepared(this, type, length, progress);
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