import java.net.*;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ATM_Server {
    private static final int PORT = 2525;
    private static final String HOST = "192.168.149.10";
    private static final String USERS_FILE = "users.json";
    private static final String LOG_FILE = "atm_server.log";
    private static Map<String, User> users = new HashMap<>();

    public static void main(String[] args) {
        loadUsers();
        log("INFO", "User data loaded successfully");
        System.out.println("User data loaded successfully");  // Terminal output
        
        try (ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName(HOST))) {
            log("INFO", "Server listening on " + HOST + ":" + PORT);
            System.out.println("Server listening on " + HOST + ":" + PORT);  // Terminal output
            
            while (true) {
                Socket client = server.accept();
                String clientInfo = client.getInetAddress() + ":" + client.getPort();
                log("INFO", "Client connected - " + clientInfo);
                System.out.println("Client connected - " + clientInfo);  // Terminal output
                new Thread(new ClientHandler(client)).start();
            }
        } catch (IOException e) {
            log("ERROR", "Server startup failed", e);
            System.err.println("Server startup failed: " + e.getMessage());  // Terminal error output
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private String currentUser;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // Original terminal output for received messages
                    System.out.println("Received from " + clientInfo + ": " + inputLine);
                    log("DEBUG", "Received from client - " + clientInfo + " -> " + inputLine);
                    
                    String[] tokens = inputLine.split(" ");
                    String response = processCommand(tokens);
                    
                    out.println(response);
                    // Original terminal output for sent messages
                    System.out.println("Sent to " + clientInfo + ": " + response);
                    log("DEBUG", "Sent to client - " + clientInfo + " <- " + response);
                }
            } catch (IOException e) {
                log("ERROR", "Client handling error - " + clientInfo, e);
                System.err.println("Client handling error (" + clientInfo + "): " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    log("INFO", "Client connection closed - " + clientInfo);
                    System.out.println("Client connection closed - " + clientInfo);
                } catch (IOException e) {
                    log("ERROR", "Socket close error - " + clientInfo, e);
                    System.err.println("Socket close error (" + clientInfo + "): " + e.getMessage());
                }
            }
        }

        private String processCommand(String[] tokens) {
            try {
                switch (tokens[0]) {
                    case "HELO":
                        currentUser = tokens[1];
                        log("INFO", "User login attempt - " + currentUser);
                        return "500 AUTH REQUIRE";
                        
                    case "PASS":
                        if (currentUser != null && users.containsKey(currentUser) 
                            && users.get(currentUser).password.equals(tokens[1])) {
                            log("INFO", "User login successful - " + currentUser);
                            return "525 OK!";
                        }
                        log("WARN", "User login failed - " + currentUser);
                        currentUser = null;
                        return "401 ERROR!";
                        
                    case "BALA":
                        if (currentUser == null) {
                            log("WARN", "Unauthenticated balance inquiry attempt");
                            return "401 ERROR!";
                        }
                        log("INFO", "Balance inquiry - " + currentUser);
                        return "AMNT:" + users.get(currentUser).balance;
                        
                    case "WDRA":
                        if (currentUser == null) {
                            log("WARN", "Unauthenticated withdrawal attempt");
                            return "401 ERROR!";
                        }
                        try {
                            double amount = Double.parseDouble(tokens[1]);  
                            User user = users.get(currentUser);
                            if (user.balance >= amount) { 
                                user.balance -= amount;
                                saveUsers();
                                log("INFO", "Withdrawal successful - " + currentUser + " Amount: " + amount);
                                return "525 OK!";
                            }
                            log("WARN", "Withdrawal failed (insufficient balance) - " + currentUser + " Amount: " + amount);
                            return "401 ERROR! Insufficient balance";
                        } catch (NumberFormatException e) {
                            log("WARN", "Invalid withdrawal amount format - " + currentUser + " Input: " + tokens[1]);
                            return "400 INVALID AMOUNT FORMAT";
                        }
                        
                    case "BYE":
                        log("INFO", "User logged out - " + currentUser);
                        return "BYE";
                        
                    default:
                        log("WARN", "Unknown command - " + Arrays.toString(tokens));
                        return "400 INVALID COMMAND";
                }
            } catch (Exception e) {
                log("ERROR", "Command processing error - " + Arrays.toString(tokens), e);
                return "400 INVALID COMMAND";
            }
        }
    }

    private static synchronized void log(String level, String message) {
        log(level, message, null);
    }

    private static synchronized void log(String level, String message, Throwable t) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append("[").append(level).append("] ");
        logEntry.append(message);
        
        if (t != null) {
            logEntry.append("\n").append("Exception stacktrace: ");
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            logEntry.append(sw.toString());
        }
        logEntry.append("\n");

        // Write to log file
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(logEntry.toString());
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    private static synchronized void loadUsers() {
        try (FileReader reader = new FileReader(USERS_FILE)) {
            JSONObject json = new JSONObject(new JSONTokener(reader));
            json.keySet().forEach(id -> 
                users.put(id, new User(
                    json.getJSONObject(id).getString("password"),
                    json.getJSONObject(id).getInt("balance")
                ))
            );
        } catch (IOException e) {
            log("WARN", "Failed to load user file, new file will be created", e);
            System.err.println("Failed to load user file, new file will be created: " + e.getMessage());
        }
    }

    private static synchronized void saveUsers() {
        JSONObject json = new JSONObject();
        users.forEach((id, user) -> 
            json.put(id, new JSONObject()
                .put("password", user.password)
                .put("balance", user.balance)
            )
        );
        
        try (FileWriter file = new FileWriter(USERS_FILE)) {
            file.write(json.toString(4));
            log("INFO", "User data saved successfully");
            System.out.println("User data saved successfully");
        } catch (IOException e) {
            log("ERROR", "Failed to save user data", e);
            System.err.println("Failed to save user data: " + e.getMessage());
        }
    }

    static class User {
        String password;
        int balance;

        public User(String password, int balance) {
            this.password = password;
            this.balance = balance;
        }
    }
}
    