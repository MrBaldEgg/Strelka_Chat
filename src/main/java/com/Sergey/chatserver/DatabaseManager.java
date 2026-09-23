package com.Sergey.chatserver;

import org.mindrot.jbcrypt.BCrypt;

import java.io.InputStream;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;


public class DatabaseManager {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static DatabaseManager instance;
    private Connection connection;

    private String url;
    private String user;
    private String password;

    private DatabaseManager(){
        loadConfig();
        try{
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(url, user, password);
            createTables();
            System.out.println("Подключение к БД установлено.");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Не удалось подключиться к БД", e);
        }
    }

    private void loadConfig(){
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")){
            if (input == null){
                throw new RuntimeException("Файл config.properties не найден в ресурсах");
            }

            props.load(input);
            this.url = props.getProperty("db.url");
            this.user = props.getProperty("db.user");
            this.password = props.getProperty("db.password");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки конфигурации");
        }
    }


    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void createTables() throws SQLException{
        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                login VARCHAR(50) UNIQUE NOT NULL,
                hashed_password VARCHAR(60) NOT NULL,
                nick VARCHAR(50) UNIQUE NOT NULL);
                """;

        String messageTable = """
                CREATE TABLE IF NOT EXISTS messages (
                id SERIAL PRIMARY KEY,
                sender VARCHAR(50) NOT NULL,
                recipient VARCHAR(50),
                message TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_private BOOLEAN DEFAULT FALSE);
                """;



        try (Statement stmt = connection.createStatement()){
            stmt.execute(usersTable);
            stmt.execute(messageTable);
            System.out.println("Таблицы созданы");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public void closeConnection(){
        try {
            if (!connection.isClosed() && connection != null) {
                connection.close();
                System.out.println("BD connection closed");
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }


    public boolean registerUser(String login, String password, String nick){
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        String sqlCommand = "INSERT INTO users (login, hashed_password, nick) VALUES (?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, login);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, nick);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean authenticateUser(String login, String password){
        String sqlCommand = "SELECT hashed_password FROM users WHERE login = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()){
                String storedHash = rs.getString("hashed_password");
                return BCrypt.checkpw(password, storedHash);

            }
        }catch (SQLException e ){
            e.printStackTrace();
        }
        return false;
    }

    public String getUserNick(String login){
        String sqlCommand = "SELECT nick FROM users WHERE login = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            if(rs.next()){
                return rs.getString("nick");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public boolean setUserNick(String login, String nick){
        String sqlCommand = "UPDATE users SET nick = ? WHERE login = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, nick);
            pstmt.setString(2, login);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    public void saveMessage(String sender, String recipient, String message, boolean isPrivate){
        String sqlCommand = "INSERT INTO messages (sender, recipient, message, is_private) VALUES (?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, message);
            pstmt.setBoolean(4, isPrivate);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public List<String> getRecentPublicMessages(int limit){
        List<String> messages = new ArrayList<>();
        String sqlCommand = """
                SELECT m.sender, m.message, m.timestamp, u.nick AS sender_nick
                FROM messages m
                LEFT JOIN users u ON m.sender = u.login
                WHERE m.recipient IS NULL 
                ORDER BY m.timestamp DESC 
                LIMIT ?
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()){
                messages.add(formatMessage(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Collections.reverse(messages);
        return messages;
    }

    public List<String> getRecentPrivateMessages(String login, int limit){
        List<String> messages = new ArrayList<>();
        String sqlCommand = """
                SELECT m.sender, m.recipient, m.message, m.timestamp,
                us.nick AS sender_nick,
                ur.nick AS recipient_nick
                FROM messages m
                LEFT JOIN users us ON m.sender = us.login
                LEFT JOIN users ur ON m.recipient = ur.login
                WHERE m.is_private = TRUE
                  AND (m.sender = ? OR m.recipient = ?)
                ORDER BY m.timestamp DESC
                LIMIT ?
                """;
        try(PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, login);
            pstmt.setString(2, login);
            pstmt.setInt(3, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()){
                String senderLogin = rs.getString("sender");
                String senderNick = rs.getString("sender_nick");
                String recipientNick = rs.getString("recipient_nick");
                if (senderNick == null) senderNick = senderLogin;
                if (recipientNick == null) recipientNick = rs.getString("recipient");
                String msg = rs.getString("message");
                String time = rs.getTimestamp("timestamp").toLocalDateTime().format(FORMATTER);
                String direction = senderLogin.equals(login)
                        ? "(YOU -> " + recipientNick + ")"
                        : "(" + senderNick + " -> YOU)";
                messages.add(String.format("[%s] %s: %s", time, direction, msg));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Collections.reverse(messages);
        return messages;
    }

    public List<String> getConversation(String login1, String login2, int limit) {
        List<String> messages = new ArrayList<>();
        String sqlCommand = """
                SELECT m.sender, m.message, m.timestamp,
                us.nick AS sender_nick
                FROM messages m
                LEFT JOIN users us ON m.sender = us.login
                WHERE m.is_private = TRUE
                AND ((m.sender = ? AND m.recipient = ?)
                OR (m.sender = ? AND m.recipient = ?))
                ORDER BY m.timestamp DESC
                LIMIT ?
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)) {
            pstmt.setString(1, login1);
            pstmt.setString(2, login2);
            pstmt.setString(3, login2);
            pstmt.setString(4, login1);
            pstmt.setInt(5, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderLogin = rs.getString("sender");
                String senderNick = rs.getString("sender_nick");
                if (senderNick == null) senderNick = senderLogin;
                String msg = rs.getString("message");
                String time = rs.getTimestamp("timestamp").toLocalDateTime().format(FORMATTER);
                String prefix = senderLogin.equals(login1) ? "YOU" : senderNick;
                messages.add(String.format("[%s] %s: %s", time, prefix, msg));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Collections.reverse(messages);
        return messages;
    }

    private String formatMessage(ResultSet rs) throws SQLException{
        String time = rs.getTimestamp("timestamp").toLocalDateTime().format(FORMATTER);
        String senderNick = rs.getString("sender");
        if (senderNick == null){
            senderNick = rs.getString("sende");
        }
        String message = rs.getString("message");
        return String.format("[%s] %s: %s", time, senderNick, message);
    }


    public String getLoginByNick(String nick) {
        String sqlCommand = """
                SELECT login FROM users WHERE nick = ?
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(sqlCommand)){
            pstmt.setString(1, nick);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()){
                return rs.getString("login");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
