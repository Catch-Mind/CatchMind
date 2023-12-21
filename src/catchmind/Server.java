package catchmind;

//GameServer.java
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.*;

import javax.swing.Timer;

public class Server {
 private final int port; // 서버 포트 번호
 private final List<ClientHandler> clients = new CopyOnWriteArrayList<>(); // 연결된 클라이언트 관리하는 리스트
 private final Random random = new Random();  // 제시어를 위한 랜덤 객체
 private final Set<String> usedWords = new HashSet<>();  // 이미 사용된 단어를 추적하기 위한 세트
 private final Map<String, Integer> scores = new ConcurrentHashMap<>(); // 각 사용자의 점수 저장하는 맵
 private final Set<String> correctGuessers = new HashSet<>(); // 정답 맞힌 사용자 저장하는 세트
 // 제시어 목록
 private final List<String> words = Arrays.asList(
       "컴퓨터", "사과", "나무", "자동차", "사랑", "튤립", "강아지", "고양이", "학교", "손",
         "향수", "마이크", "침대", "책", "지팡이", "멀티탭", "연필", "열쇠", "발자국", "세탁기",
         "올림픽", "축구", "야구", "스마트폰", "유튜브", "전구", "칫솔", "에펠탑", "선풍기", "가위",
         "포도", "탕후루", "병원", "의사", "판사", "경찰", "멀리뛰기", "배드민턴", "탁구", "마우스"
 );

 private Timer timer; //타이머
 private int timeLeft = 30; //남은 시간
 private int currentDrawingClientIndex = 0; //현재 그림을 그리는 클라이언트의 인덱스
 private String currentWord; // 현재 라운드 단어
 
 private final int maxTurns = 5;  // 게임의 최대 라운드 수
 private int currentTurn = 1; // 게임의 현재 라운드

 public ClientHandler getCurrentDrawer() {
     return clients.get(currentDrawingClientIndex);
 }
 
 public String getCurrentWord() {
     return currentWord;
 }
 
 public Set<String> getCorrectGuessers() {
     return correctGuessers;
 }
 
 public List<ClientHandler> getClients() {
     return clients;
 }

 public Map<String, Integer> getScores() {
     return scores;
 }
 
 
 public Server(int port) {
     this.port = port;
     timer = new Timer(1000, e -> {
        if (timeLeft > 0) { // 시간이 남아있으면, 모든 클라이언트에게 타이머 업데이트 전송
             broadcastMessage("TIMER " + timeLeft);
             timeLeft--;
         } else { // 시간이 다 되면 타이머를 멈추고, 그림을 그리는 사용자 변경
             timer.stop();
             if (correctGuessers.size() == clients.size() - 1) {  // 모든 유저가 정답을 맞히면, 그리는 사람 점수 업데이트
                 rewardDrawer();
             }
             changeDrawingClient();
         }
     });
 }

 // 서버 시작
 public void start() throws IOException {
     try (ServerSocket serverSocket = new ServerSocket(port)) {
         System.out.println("서버가 시작되었습니다: " + port);

         while (true) {
             Socket clientSocket = serverSocket.accept();
             String userName = "User" + (clients.size() + 1);
             ClientHandler clientHandler = new ClientHandler(clientSocket, userName, this);
             clients.add(clientHandler);
             scores.put(userName, 0);
             broadcastScoreUpdate();
             new Thread(clientHandler).start();
             broadcastMessage("CHAT " + userName + "님이 입장하셨습니다.");

             if (clients.size() == 1) {
                 changeDrawingClient();
             }
         }
     }
 }

 private void changeDrawingClient() {
     // 클라이언트가 없으면 무시
     if (clients.size() == 0) {
         return;
     }
     
     // 현재 턴이 최대 턴 수의 이하일 때
     if (currentTurn <= maxTurns) {
        // 새 라운드 시작 메시지를 모든 클라이언트에게 전송
         broadcastMessage("CHAT ======Round " + currentTurn + "======");
        // 다음으로 입장한 클라이언트가 그리기 담당자로 결정
         currentDrawingClientIndex = (currentDrawingClientIndex + 1) % clients.size();
         ClientHandler currentDrawer = clients.get(currentDrawingClientIndex);
         correctGuessers.clear();

         do {  // 랜덤으로 새로운 제시어 선택
             currentWord = words.get(random.nextInt(words.size()));
         } while (usedWords.contains(currentWord));

         usedWords.add(currentWord);
         if (usedWords.size() == words.size()) {
             usedWords.clear();
         }

         // 모든 클라이언트에게 제시어와 그리는 사람을 알림 -> 클라이언트 측에서 그림 담당자만 제시어 볼 수 있도록 해놨음
         broadcastMessage("DRAWING " + currentDrawer.getUserName());
         broadcastMessage("WORD " + currentWord);
         
         // 현재 그리기 담당자에게 차례임을 알림
         currentDrawer.send("CHAT " + "현재 당신의 차례입니다.");
         startTimer();
         currentTurn++;
     } else {  // 최대 턴수를 초과하면 게임 종료
         broadcastMessage("GAME_OVER");
         broadcastScoreUpdate();
         
         // 점수 순으로 순위 계산
         List<String> sortedUsernames = calculateRankings();

         // 각 사용자에게 최종 순위 알림
         StringBuilder popupMessage = new StringBuilder("게임이 종료되었습니다.\n\n=======[순위]=======\r\n"
               + "\n");
         for (int i = 0; i < sortedUsernames.size(); i++) {
             String username = sortedUsernames.get(i);
             int rank = i + 1;
             int score = scores.getOrDefault(username, 0);
             popupMessage.append(rank).append("등: ").append(username).append(" (").append(score).append("점)\n");
         }

         broadcastMessage("CHAT " + popupMessage.toString()); // 순위 정보를 함께 보냄

     }
 }
 
 // 그림을 그린 사용자에게 점수 보상
 private void rewardDrawer() {
    // 현재 그리기 담당 클라이언트 이름을 가져와서 점수 +10점
     String drawerName = clients.get(currentDrawingClientIndex).getUserName();
     int newScore = scores.getOrDefault(drawerName, 0) + 10;
     scores.put(drawerName, newScore);
     // 모든 플레이어에게 점수 업데이트 메시지 전송
     broadcastMessage("CHAT 모든 유저가 제시어를 맞혔습니다.\n그림을 그린 " + drawerName + "님은 점수를 획득합니다");
     broadcastScoreUpdate();
 }

 // 순위 계산
 private List<String> calculateRankings() {
    // 점수에 따라 내림차순으로 정렬
     List<String> sortedUsernames = scores.keySet().stream()
             .sorted(Comparator.comparingInt(scores::get).reversed())
             .collect(Collectors.toList());

     return sortedUsernames;
 }

 // 타이머 시작
 private void startTimer() {
     timeLeft = 30;
     timer.start();
 }

 // 모든 클라이언트에게 점수 업데이트 메시지 전송
 public void broadcastScoreUpdate() {
     StringBuilder scoreMessage = new StringBuilder("SCORES ");
     for (ClientHandler client : clients) {
         String userName = client.getUserName();
         Integer score = scores.get(userName);
         if (score != null) {
             scoreMessage.append(userName).append(": ").append(score).append("\n");
         }
     }
     broadcastMessage(scoreMessage.toString().trim());
 }
 
 // 사용자 점수 업데이트
 public void updateScore(String userName, int points) {
     Map<String, Integer> scores = getScores();
     int newScore = scores.getOrDefault(userName, 0) + points;
     scores.put(userName, newScore);
     broadcastScoreUpdate();
 }

 // 모든 클라이언트에게 메시지 전송
 public void broadcastMessage(String message) {
     for (ClientHandler client : clients) {
         client.send(message);
     }
 }
 
 public static void main(String[] args) {
     int port = 54321;
     try {
         new Server(port).start();  // 서버 시작
     } catch (IOException e) {
         e.printStackTrace();
     }
 }
}
