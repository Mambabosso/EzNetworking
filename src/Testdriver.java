import eznetworking.client.*;
import eznetworking.server.*;

import eznetworking.packet.*;


public class Testdriver {

    public static void main(String[] args) throws InterruptedException {

        Server server = new Server(4444);
        server.addServerStartedListener((s -> System.out.println("start")));
        server.addServerStoppedListener((s -> System.out.println("stop")));
        server.addPacketReceivedListener((s, c, p) -> System.out.println("New Message: " + p.unpack(String.class)));
        server.start();

        for (int i = 0; i < 4; i++) {
            Client client = new Client("localhost", 4444);
            client.connect(true);
            client.sendPacket(Packet.create("Message", "Hello Server !"));
        }

        Thread.sleep(1000);

        server.stop();
        server.disconnectClients(server.getClients());

    }
}
