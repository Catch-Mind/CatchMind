package catchmind;

//ClientHandler.java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
 private final Socket clientSocket;
 private final String userName;
 private final DataOutputStream output;
 private final Server server;

 public ClientHandler(Socket socket, String userName, Server server) throws IOException {
     this.clientSocket = socket;
     this.userName = userName;
     this.output = new DataOutputStream(socket.getOutputStream());
     this.server = server;
 }
 
 public String getUserName() {
     return userName;
 }

 public void run() {
     try (DataInputStream input = new DataInputStream(clientSocket.getInputStream())) {
         send("USERNAME " + userName);
         String inputLine;
         while ((inputLine = input.readUTF()) != null) {  
             if (inputLine.startsWith("CHAT ")) {   // CHAT으로 시작하는 메시지인 경우
                 String message = inputLine.substring(5);
                 // 메시지 파싱 -> 정답 맞힌 경우
                 if (!server.getCurrentDrawer().getUserName().equals(userName) &&
                         message.equalsIgnoreCase(server.getCurrentWord()) &&
                         !server.getCorrectGuessers().contains(userName)) {
                    server.getCorrectGuessers().add(userName);
                     // 첫 정답자에게는 +15점, 그 이후는 +10점 부여
                    server.updateScore(userName, server.getCorrectGuessers().size() == 1 ? 15 : 10);
                     send("CHAT 정답을 맞추셨습니다!");
                     server.broadcastMessage("CHAT " + userName + "님이 정답을 맞추셨습니다!");
                 } else {
                    server.broadcastMessage("CHAT " + userName + ": " + message);
                 }
             } else if (inputLine.startsWith("DRAW ") || inputLine.startsWith("SHAPE ") || inputLine.startsWith("ERASE ")) {   // DRAW, SHAPE, ERASE로 시작하는 메시지인 경우
                if (server.getCurrentDrawer().getUserName().equals(userName)) {
                   server.broadcastMessage(inputLine);
                 }
             }
         }
     } catch (IOException e) {
         System.out.println(userName + "의 연결이 끊어졌습니다.");
     } finally {
        server.getClients().remove(this);
        server.getScores().remove(userName);
        server.broadcastScoreUpdate();
         try {
             clientSocket.close();
         } catch (IOException e) {
             e.printStackTrace();
         }
         server.broadcastMessage("CHAT " + userName + "님이 퇴장하셨습니다.");
     }
 }
 
 // 클라이언트에게 메시지 전송
 public void send(String message) {
     try {
         output.writeUTF(message);
     } catch (IOException e) {
         System.out.println("메시지 전송 실패: " + userName + " " + e.getMessage());
     }
 }
}