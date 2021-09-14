package com.example.tello_neurosky;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class TelloDrone {
    InetAddress IPAddress = null;
    boolean isUp = false;
    boolean ready = false;
    String lastAnswer = "";
    long cooldown = 0;

    public TelloDrone() {
        try {
            IPAddress = InetAddress.getByName("192.168.10.1");
            //setCommand("command");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

    }

    public String setCommand(String command) {
        return setCommand(command, null);
    }

    public String setCommand(String command, Runnable callback) {

        System.out.println("Trying command " + command);
        if (!ready)
            return null;
/*
        if (System.currentTimeMillis() - cooldown < 3000) {
            return null;
        } else {
            cooldown = System.currentTimeMillis();
        }
*/
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Command " + command);
                        if (command.startsWith("takeoff"))
                            isUp = true;
                        else if (command.startsWith("land"))
                            isUp = false;
                        byte[] sendingDataBuffer = new byte[1024];
                        byte[] receivingDataBuffer = new byte[1024];
                        sendingDataBuffer = command.getBytes();
                        DatagramSocket clientSocket = null;
                        try {
                            clientSocket = new DatagramSocket();

                            DatagramPacket sendingPacket = new DatagramPacket(sendingDataBuffer, sendingDataBuffer.length, IPAddress, 8889);

                            clientSocket.send(sendingPacket);

                            DatagramPacket receivingPacket = new DatagramPacket(receivingDataBuffer, receivingDataBuffer.length);
                            clientSocket.receive(receivingPacket);

                            // Выведите на экране полученные данные
                            String receivedData = new String(receivingPacket.getData());
                            System.out.println("Response " + receivedData);

                            lastAnswer = receivedData;

                            if (callback != null)
                                callback.run();


                        } catch (SocketException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
        ).start();

        return null;
    }
}
