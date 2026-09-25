package com.Sergey.chatserver;

import java.io.*;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String name;
    private boolean authenticated = false;
    private String login;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.name = "User" + System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);


            out.println("SERVER: Добро пожаловать в чат \n" +
                    "Пожалуйста войдите или зарегистрируйтесь");
            out.println("Доступные команды: \n" +
                    "/register <Login> <Password> <Nick> \n" +
                    "/login <Login> <Password>\n" +
                    "/exit");

            // Обработка ввода пользователя
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("/")){
                    handleCommand(message);
                } else if (!authenticated) {
                    out.println("ОШИБКА - сначала войдите (/login) или зарегистрируйтесь (/register)");
                } else {
                    ChatServer.broadcast(name + ": " + message, this);
                    DatabaseManager db = DatabaseManager.getInstance();
                    db.saveMessage(this.login, null, message, false);
                }
            }

        } catch (IOException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } finally {

            if (authenticated){
                ChatServer.removeClient(this);
                ChatServer.broadcastToAll("SERVER: " + this.name + " покинул чат.");
            }
            closeResources();
        }
    }



    private void handleCommand(String command) {
        String cmd = command.split(" ")[0].toLowerCase(Locale.ROOT);
        DatabaseManager db = DatabaseManager.getInstance();
        String[] parts;


        switch (cmd) {
            case "/nick":
                parts = command.split(" ", 2);

                if (!authenticated){
                    out.println("ОШИБКА - сначала войдите (/login) или зарегистрируйтесь (/register)");
                    return;
                }

                if (parts.length < 2) {
                    out.println("ОШИБКА - укажите новое имя. Пример - /nick Sergey");
                    return;
                }
                String newName = parts[1];
                if (newName.isEmpty() || newName.contains(" ")) {
                    out.println("ОШИБКА - имя не должно быть пустым или содержать пробелы");
                    return;
                }
                String oldName = this.name;
                boolean nickChangeResult = db.setUserNick(this.login, newName);
                if (nickChangeResult){
                    this.name = newName;
                    ChatServer.broadcast("SERVER: " + oldName + " сменил имя на - " + newName, this);
                    out.println("Ваше имя изменено на - " + this.name);
                } else{
                    out.println("ОШИБКА - не удалось изменить НИК!");
                }
                break;


            case "/w":
                parts = command.split(" ", 3);
                if (!authenticated){
                    out.println("ОШИБКА - сначала войдите (/login) или зарегистрируйтесь (/register)");
                    return;
                }
                if (parts.length < 3) {
                    out.println("ОШИБКА - Укажите: 1.Комманду 2.Поулчателя 3.Сообщение \n " +
                            " Пример - /w Андрей Привет, как дела?");
                    return;
                }
                String recipientName = parts[1];
                String privateMessage = parts[2];

                ClientHandler recipient = ChatServer.findClientByName(recipientName);
                if (recipient == null) {
                    out.println("ОШИБКА - Пользователь [" + recipientName + "] не найден!");
                } else if (recipient == this) {
                    out.println(("ОШИБКА - Шизоид? Зачем себе пытаешься отправить?"));
                } else {
                    recipient.sendMessage("(Лично от " + this.getName() + "):  " + privateMessage);
                    this.sendMessage("(Лично для " + recipientName + "):  " + privateMessage);
                    db.saveMessage(this.login, recipient.getLogin(), privateMessage, true);
                }
                break;



            case "/register":
                if (authenticated){
                    out.println("Вы уже вошли.");
                    return;
                }



                parts = command.split(" ", 4);
                if (parts.length < 4){
                    out.println("ОШИБКА - Введите Логин, Пароль и Никнейм для регистрации \n" +
                            "Пример - /register Login Password Nickname");
                    return;
                }
                String newLogin = parts[1];
                String newPassword = parts[2];
                String newNick = parts[3];


                if (newNick.contains(" ")) {
                    out.println("ОШИБКА - имя не должно быть содержать пробелы");
                    return;
                }

                try {
                    boolean registrationResult = db.registerUser(newLogin, newPassword, newNick);
                    if (registrationResult) {
                        out.println("Успешная регистрация! \n" +
                                "Ваш логин - " + newLogin + "\n" +
                                "Ваш пароль - " + newPassword + "\n" +
                                "Ваш никнейм - " + newNick);
                    } else {
                        out.println("ОШИБКА - такой Логин или Никнейм уже существует.");
                    }
                    authenticateUser(db, newLogin, newPassword);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case "/login":
                if (authenticated){
                    out.println("Вы уже вошли.");
                    return;
                }
                parts = command.split(" ", 3);
                if (parts.length < 3 || parts[2].isEmpty()){
                    out.println("ОШИБКА - Введите Логин и Пароль для входа в систему \n" +
                            "Пример - /login <Login> <Password>" );
                    return;
                }
                String login = parts[1];
                String password = parts[2];

                try {
                    authenticateUser(db, login, password);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case "/history":
                if (!authenticated){
                    out.println("ОШИБКА - сначала войдите (/login) или зарегистрируйтесь (/register)");
                    return;
                }

                parts = command.split(" ", 2);
                if (parts.length < 2){
                    List<String> publicHistory = db.getRecentPublicMessages(50);
                    if (!publicHistory.isEmpty()){
                        out.println("=== Последние сообщения общего чата ===");
                        for (String msg : publicHistory){
                            out.println(msg);
                        }
                        out.println("=== Конец истории ===");
                    } else {
                        out.println("(Общий чат пуст)");
                    }

                } else if (parts[1].equals("private")){
                    List<String> privateHistory = db.getRecentPrivateMessages(this.login, 50);
                    if (!privateHistory.isEmpty()){
                        out.println("=== История приватных сообщений ===");
                        for (String msg : privateHistory){
                            out.println(msg);
                        }
                        out.println("=== Конец истории ===");
                    } else{
                        out.println("(История приватных сообщений пуста)");
                    }
                } else {
                    String otherNick = parts[1];
                    String otherLogin = db.getLoginByNick(otherNick);
                    if (otherLogin == null) {
                        out.println("ОШИБКА - пользователь с ником [" + otherNick + "] не найден.");
                        return;
                    }

                    List<String> conversation = db.getConversation(this.login, otherLogin, 50);
                    if (conversation.isEmpty()){
                        out.println("(Переписка с [" + otherNick + "] пуста)" );
                    } else{
                        out.println("=== Переписка с " + otherNick + " ===");
                        for (String msg: conversation){
                            out.println(msg);
                        }
                        out.println("=== Конец переписки ===");
                    }
                }
                break;

            case "/online":
                if (!authenticated){
                    out.println("ОШИБКА - сначала войдите (/login) или зарегистрируйтесь (/register)");
                }

                List<String> onlineUsers = ChatServer.getAllOnlineUsersNicks();
                Collections.sort(onlineUsers);
                out.println("=== Список пользователей онлайн ===");
                for (String onlineNick : onlineUsers){
                    out.println(onlineNick);
                }
                out.println("=== Конец списка ===");
                break;



            case "/exit":
                out.println("До свидания, " +  this.name);

                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;




            default:
                out.println("Неизвестная команда.\n" +
                        "/ - список доступных команд");


        }
    }

    private void authenticateUser (DatabaseManager db, String login, String password){
        boolean authenticationResult = db.authenticateUser(login, password);
        if (authenticationResult){
            String nick = db.getUserNick(login);
            if (nick != null){
                this.name = nick;
            } else {
                this.name = "User" + System.currentTimeMillis();
            }
            out.println("Вы вошли! Добро пожаловать - " + this.name);

            List<String> publicHistory = db.getRecentPublicMessages(50);

            if (!publicHistory.isEmpty()){
                out.println("=== Последние сообщения общего чата ===");
                for (String msg : publicHistory){
                    out.println(msg);
                }
                out.println("=== Конец истории ===");
            } else {
                out.println("(Общий чат пуст)");
            }

            ChatServer.addClient(this);
            this.login = login;
            this.authenticated = true;
            ChatServer.broadcast("SERVER: " + name + " присоединился к чату.", this);

        } else {
            out.println("ОШИБКА - Пользователь не найден, проверьте введенные данные");
        }
    }




    public void sendMessage(String message) {
        out.println(message);
    }

    private void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public String getLogin(){
        return login;
    }


    public boolean verifyUser(){
        return authenticated;
    }
}


