package com.Sergey.chatserver;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {

    public static final int PORT = 8189;
    private static List<ClientHandler> clients = new CopyOnWriteArrayList<>();


    public static void main(String[] args) {

        DatabaseManager db = DatabaseManager.getInstance();


        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Запуск сервера, ПОРТ = " + PORT);

            while (true){
                Socket clientSocket = serverSocket.accept();
                System.out.println(" Новый клиент подключен: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
            }



        } catch (IOException e ){
            System.err.println("Ошибка сервера " + e.getMessage());
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            db.closeConnection();
            System.out.println("Сервер завершен.");
        }));
    }

    public static void addClient(ClientHandler client){
        clients.add(client);
    }

    public static void removeClient(ClientHandler client){
        clients.remove(client);
    }

    public static void broadcast (String message, ClientHandler sender){
        for (ClientHandler client : clients){
            if (client != sender && client.verifyUser()){
                client.sendMessage(message);
            }
        }
    }

    public static void broadcastToAll (String message){
        for (ClientHandler client : clients){
            client.sendMessage(message);
        }
    }



    public static ClientHandler findClientByName (String name) {
        for (ClientHandler client : clients){
            if (client.getName().equals(name)){
                return client;
            }
        }
        return null;
    }
}
