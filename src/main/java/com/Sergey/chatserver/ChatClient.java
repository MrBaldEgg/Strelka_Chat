package com.Sergey.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private static final String HOST = "localhost";
    private static final int PORT = 8189;
    private static boolean soundEnabled = true;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out  = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))){

            System.out.println(Colors.CYAN + "Успешное подключение к серверу. Введите / для списка команд." + Colors.RESET);

            Thread readerThread = new Thread(() -> {
                try{
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null){
                        printColored(serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println(Colors.RED + "Соединение потеряно" + Colors.RESET);
                }
            });

            readerThread.setDaemon(true);
            readerThread.start();


            String userInput;
            while ((userInput = console.readLine()) != null ){

                if (userInput.trim().equals("/")){
                    printHelp();
                    continue;
                }

                if (userInput.trim().equalsIgnoreCase("/beep")){
                    soundEnabled = !soundEnabled;
                    System.out.println(Colors.CYAN + "Звук уведомлений " + (soundEnabled ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН")  + Colors.RESET);
                    continue;
                }

                out.println(userInput);

                if (!userInput.startsWith("/")){
                    System.out.println(Colors.GRAY + "YOU: " + userInput + Colors.RESET );
                }

            }
            System.out.println(Colors.CYAN + "Отключение от сервера" + Colors.RESET);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void printColored(String message) {
        String color;
        if (message.startsWith("SERVER:")) {
            color = Colors.YELLOW;
        } else if (message.startsWith("(Лично")) {
            color = Colors.GREEN;
            if (message.startsWith("(Лично от")) {
                beep();
            }
        } else if (message.startsWith("===")) {
            color = Colors.CYAN;
        } else if (message.startsWith("ОШИБКА") || message.startsWith("Ошибка")) {
            color = Colors.RED;
        } else {
            color = Colors.RESET;
        }
        System.out.println(color + message + Colors.RESET);
    }



    private static void printHelp() {
        System.out.println(Colors.CYAN + "Доступные команды:" + Colors.RESET);
        System.out.println(Colors.GREEN + "  /register <логин> <пароль> <ник>" + Colors.RESET + " — регистрация");
        System.out.println(Colors.GREEN + "  /login <логин> <пароль>" + Colors.RESET + " — вход");
        System.out.println(Colors.GREEN + "  /nick <новый_ник>" + Colors.RESET + " — сменить ник");
        System.out.println(Colors.GREEN + "  /w <ник> <сообщение>" + Colors.RESET + " — личное сообщение");
        System.out.println(Colors.GREEN + "  /history" + Colors.RESET + " — история общего чата");
        System.out.println(Colors.GREEN + "  /history private" + Colors.RESET + " — ваши личные сообщения");
        System.out.println(Colors.GREEN + "  /history <ник>" + Colors.RESET + " — переписка с пользователем");
        System.out.println(Colors.GREEN + "  /online" + Colors.RESET + " — список онлайн");
        System.out.println(Colors.GREEN + "  /beep" + Colors.RESET + " — ВКЛЮЧИТЬ / ВЫКЛЮЧИТЬ звук уведомлений");
        System.out.println(Colors.GREEN + "  /exit" + Colors.RESET + " — выход");
    }

    private static void beep(){
        if (soundEnabled){
            System.out.print("\u0007");
            System.out.flush();
        }
    }
}
