package com.Sergey.chatserver;

import java.io.*;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
                    "/register <Login> <password> \n" +
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
                    db.saveMessage(this.name, null, message, false);
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
                    db.saveMessage(this.name, recipientName, privateMessage, true);
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
                if (parts.length < 3){
                    out.println("ОШИБКА - Введите Логин и Пароль для входа в систему \n" +
                            "Пример - /login Login Password" );
                    return;
                }
                String login = parts[1];
                String password = parts[2];
                String nick;

                try {
                    boolean authenticationResult = db.authenticateUser(login, password);
                    if (authenticationResult){

                        List<String> recentMessages = db.getRecentMessages(50);
                        nick = db.getUserNick(login);

                        if (nick != null){
                            this.name = nick;
                        } else {
                            this.name = "User" + System.currentTimeMillis();
                        }

                        for (String message : recentMessages){
                            out.println(message);
                        }

                        ChatServer.addClient(this);
                        this.login = login;
                        this.authenticated = true;
                        out.println("Успешно! Добро пожаловать - " + this.name);
                        ChatServer.broadcast("SERVER: " + name + " присоединился к чату.", this);

                    } else {
                        out.println("ОШИБКА - Пользователь не найден, проверьте введенные данные");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
                out.println("Неизвестная команда. Доступно: \n " +
                        "/nick <новое имя>\n" +
                        "/w <имя> <личное сообщение>\n" +
                        "/register <Login> <password> \n" +
                        "/login <Login> <Password>\n" +
                        "/exit");
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


    public boolean verifyUser(){
        return authenticated;
    }
}


