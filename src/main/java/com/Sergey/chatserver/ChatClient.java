package com.Sergey.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private static final String HOST = "localhost";
    private static final int PORT = 8189;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out  = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))){

            System.out.println("Успешное подключение к серверу");

            Thread readerThread = new Thread(() -> {
                try{
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null){
                        System.out.println(serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("Соединение потеряно");
                }
            });

            readerThread.setDaemon(true);
            readerThread.start();


            String userInput;
            while ((userInput = console.readLine()) != null ){
                out.println(userInput);
            }
            System.out.println("Отключение от сервера");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
