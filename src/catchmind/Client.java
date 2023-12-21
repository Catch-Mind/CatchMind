package catchmind;

//Client.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class Client {
 // GUI 컴포넌트
  private JFrame frame;
  private JPanel drawingPanel;
  private JTextArea chatArea;
  private JTextField chatInput;
  private JLabel timerLabel;
  private JLabel wordLabel;
  private JTextArea scoresArea;
  
  // 소켓, 데이터스트림
  private Socket socket;
  private DataOutputStream output;
 
  // 그림 그리기의 좌표, 색상, 모드
  private int lastX = -1, lastY = -1;
  private int startX = -1, startY = -1;
  private Color currentColor = Color.BLACK;
  private Mode currentMode = Mode.DRAW;
  
  private String currentDrawer = ""; // 현재 그림 그리는 사용자의 이름
  private String userName;  // 접속한 클라이언트 사용자 이름
  
  // 그리기 모드 정의(그리기, 지우기, 빈 사각형, 채워진 사각형, 빈 원, 채워진 원
  private enum Mode { DRAW, ERASE, RECTANGLE, FILLED_RECTANGLE, OVAL, FILLED_OVAL }

  public Client(String serverAddress, int serverPort) {
      try {
         // 서버에 연결
          socket = new Socket(serverAddress, serverPort);
          output = new DataOutputStream(socket.getOutputStream());
          setupUI();
          new Thread(this::listenToServer).start();
      } catch (IOException e) {
          e.printStackTrace();
          System.exit(1);  // 연결 실패 시 종료
      }
  }

  // 레이아웃 설정
  private void setupUI() {
      frame = new JFrame("캐치 마인드");   // 제목
      frame.setLayout(new BorderLayout());

      // 그림 그리는 부분
      drawingPanel = new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
              super.paintComponent(g);
              drawFallingDesign(g); // 배경 디자인 그리는 메서드 호출
          }
      };
      drawingPanel.setPreferredSize(new Dimension(450, 300));
      drawingPanel.setBackground(Color.WHITE);

      drawingPanel.addMouseListener(new MouseAdapter() {
          @Override
          // 마우스 버튼이 눌려질 때 호출
          public void mousePressed(MouseEvent e) {
             if (!currentDrawer.equals(userName)) return; // 현재 그릴 차례가 아닐 경우 무시
             // 마우스가 눌러진 위치의 x, y 좌표 저장
              startX = e.getX();
              startY = e.getY();
              // 마지막으로 그린 위치를 리셋하여 자유롭게 그릴 수 있도록 함
              lastX = -1;
              lastY = -1;
          }

          @Override
          // 마우스 버튼이 놓아질 때 호출
          public void mouseReleased(MouseEvent e) {
             // 도형 그리기 모드
              if (currentMode != Mode.DRAW && startX != -1 && startY != -1) {
                 // 마우스를 놓은 위치의 x, y 좌표 저장
                  int endX = e.getX();
                  int endY = e.getY();
                  Graphics g = drawingPanel.getGraphics();
                  g.setColor(currentColor);
                  // 시작과 끝점 사이의 거리 계산 -> 너비와 높이 결정
                  int width = Math.abs(endX - startX);
                  int height = Math.abs(endY - startY);
                  // 왼쪽 상단의 모서리 좌표
                  int x = Math.min(startX, endX);
                  int y = Math.min(startY, endY);
                  // 선택된 모드로 도형 그리기
                  drawShape(g, x, y, width, height, currentMode);
                  // 시작 위치 리셋
                  startX = -1;
                  startY = -1;
              }
          }

      });

      drawingPanel.addMouseMotionListener(new MouseMotionAdapter() {
          @Override
          // 마우스가 드래그될 때 호출
          public void mouseDragged(MouseEvent e) {
             if (!currentDrawer.equals(userName)) return;   // 현재 그릴 차례가 아닌 경우 무시
             // 그리기 모드이거나 지우기 모드일 경우
                   if (currentMode == Mode.DRAW || currentMode == Mode.ERASE) {
                    // 마우스 드래그 하는 동안 계속 선 그리기 -> 드롭 시에만 그려지는 것이 아님
                     int x = e.getX();
                     int y = e.getY();
                     Graphics g = drawingPanel.getGraphics();
                     g.setColor((currentMode == Mode.ERASE) ? Color.WHITE : currentColor);
 
                     if (lastX != -1 && lastY != -1) {
                         if (currentMode == Mode.DRAW) {
                             g.drawLine(lastX, lastY, x, y);
                             sendDrawCommand(lastX, lastY, x, y);   // 서버에 그리기 명령 보냄
                         } else if (currentMode == Mode.ERASE) {
                             int brushSize = 20;
                             g.fillRect(x - brushSize / 2, y - brushSize / 2, brushSize, brushSize);
                             sendEraseCommand(x, y, brushSize);   // 서버에 지우기 명령 보냄
                         }
                     }
                     // 현재 그린 위치를 마지막 위치로 갱신
                     lastX = x;
                     lastY = y;
                 }
          }
      });

      // 제시어
      JPanel colorPanel = new JPanel();
      wordLabel = new JLabel("", SwingConstants.CENTER);
      colorPanel.add(wordLabel);
      // 색 선택
      addColorButtons(colorPanel);
      // 타이머
      timerLabel = new JLabel("남은 시간: 30초", SwingConstants.CENTER);
      colorPanel.add(timerLabel);
      // 펜, 모양, 지우개 선택 부분
      JPanel shapePanel = new JPanel();
      shapePanel.setLayout(new BoxLayout(shapePanel, BoxLayout.Y_AXIS));
      addShapeButtons(shapePanel);

      // 점수 출력 부분
      JLabel scoresLabel = new JLabel("[점수]");
      scoresLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      scoresLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
      shapePanel.add(scoresLabel);

      scoresArea = new JTextArea();
      scoresArea.setEditable(false);
      scoresArea.setOpaque(false);
      scoresArea.setMargin(new Insets(5, 10, 5, 10));

      JScrollPane scoresScroll = new JScrollPane(scoresArea);
      scoresScroll.setPreferredSize(new Dimension(50, 100));
      scoresScroll.setOpaque(false);
      scoresScroll.getViewport().setOpaque(false);
      scoresScroll.setBorder(null);

      shapePanel.add(scoresScroll);
      
      // 채팅 내용 목록
      chatArea = new JTextArea();
      chatArea.setEditable(false);
      JScrollPane chatScroll = new JScrollPane(chatArea);
      chatScroll.setPreferredSize(new Dimension(245, 300));
     // 채팅 내용 작성 input
      chatInput = new JTextField();
      chatInput.addActionListener(e -> sendChatMessage());

      frame.add(chatScroll, BorderLayout.EAST);
      frame.add(chatInput, BorderLayout.SOUTH);
      frame.add(drawingPanel, BorderLayout.CENTER);
      frame.add(colorPanel, BorderLayout.NORTH);
      frame.add(shapePanel, BorderLayout.WEST);
      

      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.pack();
      frame.setVisible(true);
  }
  
  //그림 그리는 부분 스케치북처럼 꾸미기
  private void drawFallingDesign(Graphics g) {
      int numCircles = 40;  
      int circleRadius = 12;  
      int spaceBetweenCircles = 25;  
      int lineLength = 15; 
      int startY = 0;  

      for (int i = 0; i < numCircles; i++) {
          g.setColor(Color.BLACK);
          g.fillOval(20, startY + i * spaceBetweenCircles, circleRadius, circleRadius);
          g.drawLine(20 + circleRadius / 2, startY + i * spaceBetweenCircles + circleRadius / 2,
                     20 + circleRadius / 2 - lineLength, startY + i * spaceBetweenCircles + circleRadius / 2);
      }
  }
  // 색상 선택 버튼 추가하기
  private void addColorButtons(JPanel panel) {
      Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, new Color(128, 0, 128)};
      String[] colorNames = {"빨간색", "주황색", "노란색", "초록색", "파란색", "보라색"};
      for (int i = 0; i < colors.length; i++) {
          JButton colorButton = new JButton(colorNames[i]);
          Color color = colors[i];
          colorButton.setBackground(color);
          colorButton.setOpaque(true);
          colorButton.setBorderPainted(false);
          colorButton.setFocusPainted(false);
          // 버튼 클릭 시 해당 색상으로 currentColor 설정
          colorButton.addActionListener(e -> currentColor = color);
          panel.add(colorButton);
      }
  }
  // 도형 선택 버튼 추가하기
  private void addShapeButtons(JPanel panel) {
      String[] shapeNames = {"펜", "빈 사각형", "채워진 사각형", "빈 원", "채워진 원", "지우개"};
      Mode[] shapeModes = {Mode.DRAW, Mode.RECTANGLE, Mode.FILLED_RECTANGLE, Mode.OVAL, Mode.FILLED_OVAL, Mode.ERASE};

      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      for (int i = 0; i < shapeNames.length; i++) {
          JButton shapeButton = new JButton(shapeNames[i]);
          Mode mode = shapeModes[i];
          // 버튼 클릭 시 해당 도형 모드로 currentMode 설정
          shapeButton.addActionListener(e -> currentMode = mode);
          shapeButton.setFocusPainted(false);
          shapeButton.setBackground(new Color(191, 239, 255));

          shapeButton.addMouseListener(new MouseAdapter() {
              @Override
              public void mouseClicked(MouseEvent e) {
                 // 모든 버튼의 배경 색상 초기화 -> 연한 하늘
                  for (Component component : panel.getComponents()) {
                      if (component instanceof JButton) {
                          ((JButton) component).setBackground(new Color(191, 239, 255));
                      }
                  }

                  // 클릭한 버튼의 배경 색상을 변경 -> 진한 하늘
                  shapeButton.setBackground(new Color(0, 191, 255));
              }
          });

          panel.add(shapeButton);
      }
  }

  // 도형 그리기
  private void drawShape(Graphics g, int x, int y, int width, int height, Mode mode) {
      switch (mode) { // 선택된 모드에 따라 그래픽 객체 사용하여 도형 그리기
          case RECTANGLE: // 빈 사각형
              g.drawRect(x, y, width, height);
              sendShapeCommand(x, y, width, height, "RECTANGLE");   // 서버에 빈 사각형 그리기 명령 보냄
              break;
          case FILLED_RECTANGLE:  // 채워진 사각형
              g.fillRect(x, y, width, height);
              sendShapeCommand(x, y, width, height, "FILLED_RECTANGLE"); // 서버에 채워진 사각형 그리기 명령 보냄
              break;
          case OVAL:  // 빈 원
              g.drawOval(x, y, width, height);
              sendShapeCommand(x, y, width, height, "OVAL");   // 서버에 빈 원 그리기 명령 보냄
              break;
          case FILLED_OVAL:  // 채워진 원
              g.fillOval(x, y, width, height);
              sendShapeCommand(x, y, width, height, "FILLED_OVAL");   // 서버에 채워진 원 그리기 명령 보냄
              break;
      }
  }
  // 그리는 부분 초기화
  private void clearDrawingPanel() {
     Graphics g = drawingPanel.getGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, drawingPanel.getWidth(), drawingPanel.getHeight());
      
      drawFallingDesign(g);
  }
  // 서버에 그리기 명령 전송
  private void sendDrawCommand(int startX, int startY, int endX, int endY) {
      try {
         // 시작과 끝 좌표, 색상 정보
          output.writeUTF("DRAW " + startX + " " + startY + " " + endX + " " + endY + " " + currentColor.getRGB());
          output.flush();
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
  // 서버에 도형 그리기 명령 전송
  private void sendShapeCommand(int x, int y, int width, int height, String shapeType) {
      try {
         // 도형 타입, 왼쪽 상단 좌표, 너비, 높이, 색상 정보
          output.writeUTF("SHAPE " + shapeType + " " + x + " " + y + " " + width + " " + height + " " + currentColor.getRGB());
          output.flush();
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
  // 서버에 지우기 명령 전송
  private void sendEraseCommand(int x, int y, int brushSize) {
      try {
         // 지우개 중심 좌표, 지우개 크기 정보
          output.writeUTF("ERASE " + x + " " + y + " " + brushSize);
          output.flush();
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
  // 서버에 채팅 메시지 전송
  private void sendChatMessage() {
      String message = chatInput.getText();
      if (!message.isEmpty()) {
          try {
              output.writeUTF("CHAT " + message);
              output.flush();
              chatInput.setText("");
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
  }
  // 서버로부터 메시지 수신 후 처리
  private void listenToServer() {
      try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
          while (true) {
              String serverMessage = input.readUTF();
              if (serverMessage.startsWith("DRAW ")) {   // DRAW 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 그리기 명령에 필요한 좌표, 색상 값
                  String[] drawCommand = serverMessage.substring(5).split(" ");
                  int x1 = Integer.parseInt(drawCommand[0]);
                  int y1 = Integer.parseInt(drawCommand[1]);
                  int x2 = Integer.parseInt(drawCommand[2]);
                  int y2 = Integer.parseInt(drawCommand[3]);
                  Color lineColor = new Color(Integer.parseInt(drawCommand[4]));
                  SwingUtilities.invokeLater(() -> {
                      Graphics g = drawingPanel.getGraphics();
                      g.setColor(lineColor);
                      g.drawLine(x1, y1, x2, y2);
                  });
              } else if (serverMessage.startsWith("CHAT ")) {   // CHAT 로 시작하는 명령일 경우
                  String chatMessage = serverMessage.substring(5);
                  // 채팅 내용 목록에 메시지 내용 추가
                  SwingUtilities.invokeLater(() -> {
                      chatArea.append(chatMessage + "\n");
                  });
              } else if (serverMessage.startsWith("SHAPE ")) {   // SHAPE 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 도형 타입, 왼쪽 상단 좌표, 너비, 높이, 색상 값
                  String[] shapeCommand = serverMessage.substring(6).split(" ");
                  String shapeType = shapeCommand[0];
                  int x = Integer.parseInt(shapeCommand[1]);
                  int y = Integer.parseInt(shapeCommand[2]);
                  int width = Integer.parseInt(shapeCommand[3]);
                  int height = Integer.parseInt(shapeCommand[4]);
                  Color shapeColor = new Color(Integer.parseInt(shapeCommand[5]));
                  SwingUtilities.invokeLater(() -> {
                      Graphics g = drawingPanel.getGraphics();
                      g.setColor(shapeColor);
                      switch (shapeType) {   // 도형 타입
                          case "RECTANGLE":
                              g.drawRect(x, y, width, height);   // 빈 사각형 그리기
                              break;
                          case "FILLED_RECTANGLE":
                              g.fillRect(x, y, width, height);   // 채워진 사각형 그리기
                              break;
                          case "OVAL":
                              g.drawOval(x, y, width, height);   // 빈 원 그리기
                              break;
                          case "FILLED_OVAL":
                              g.fillOval(x, y, width, height);   // 채워진 원 그리기
                              break;
                      }
                  });
              } else if (serverMessage.startsWith("ERASE ")) {   // ERASE 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 지우개 중심 좌표, 지우개 크기 값
                 String[] eraseCommand = serverMessage.substring(6).split(" ");
                  int x = Integer.parseInt(eraseCommand[0]);
                  int y = Integer.parseInt(eraseCommand[1]);
                  int brushSize = Integer.parseInt(eraseCommand[2]);
                  SwingUtilities.invokeLater(() -> {
                      Graphics g = drawingPanel.getGraphics();
                      g.setColor(Color.WHITE);
                      g.fillRect(x - brushSize / 2, y - brushSize / 2, brushSize, brushSize);
                  });
              } else if (serverMessage.startsWith("TIMER ")) {   // TIMER 로 시작하는 명령일 경우
                  String timerMessage = serverMessage.substring(6);
                  // 타이머 라벨 업데이트
                  SwingUtilities.invokeLater(() -> {
                      timerLabel.setText("남은 시간: " + timerMessage + "초");
                  });
              } else if (serverMessage.startsWith("DRAWING ")) {   // DRAWING 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 현재 그림 그리는 사용자의 이름 추출
                  currentDrawer = serverMessage.substring(8);
                  System.out.println("서버로부터 받은 현재 draw 담당: " + currentDrawer);
                  SwingUtilities.invokeLater(() -> {
                      chatArea.append("현재 그리기 유저: " + currentDrawer + "\n\n");
                      // 현재 그리기 담당이 아닌 클라이언트는 제시어 감추기
                      if (!currentDrawer.equals(userName)) {
                          wordLabel.setText("");
                      }
                  });
              } else if (serverMessage.startsWith("USERNAME ")) {   // USERNAME 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 현재 접속한 사용자 이름 추출
                  userName = serverMessage.substring(9);
                  System.out.println("서버로부터 받은 userName: " + userName);
                  // 메인 윈도우의 제목을 사용자 이름으로 설정
                  SwingUtilities.invokeLater(() -> {
                      frame.setTitle("캐치 마인드(" + userName + ")");
                  });
              } else if (serverMessage.startsWith("WORD ")) {   // WORD 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 현재 제시어 추출
                  String word = serverMessage.substring(5);
                  clearDrawingPanel();   // 그리기 담당자가 바뀔 때마다 그리는 부분 초기화
                  SwingUtilities.invokeLater(() -> {
                     // 현재 그리기 담당이라면 제시어 라벨 보이기
                      if (currentDrawer.equals(userName)) {
                          wordLabel.setText("제시어: " + word);
                      }
                  });
              } else if (serverMessage.startsWith("SCORES ")) {   // SCORES 로 시작하는 명령일 경우
                 // 메시지 파싱 -> 점수 정보 추출
                  String scores = serverMessage.substring(7);
                  // 점수 영역의 텍스트 업데이트
                  SwingUtilities.invokeLater(() -> {
                      scoresArea.setText(scores);
                  });
              } else if (serverMessage.equals("GAME_OVER")) {   // GAME_OVER의 명령일 경우
                  // 게임 종료 메시지를 감지하면 팝업 창 띄우기
                  SwingUtilities.invokeLater(() -> {
                      timerLabel.setText("");
                      JOptionPane.showMessageDialog(frame, "게임이 종료되었습니다.\n\n" + extractRankingInfo(serverMessage),"게임 종료", JOptionPane.INFORMATION_MESSAGE);
              
                  });
              }
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
  }

  // 순위 정보를 추출하는 메서드
  private String extractRankingInfo(String serverMessage) {
      if (serverMessage.startsWith("CHAT ")) {
          return serverMessage.substring(5);
      }
      return "";
  }
  
  public static void main(String[] args) {
      String serverAddress = "localhost"; // 서버 주소
      int serverPort = 54321;  // 포트 번호
      new Client(serverAddress, serverPort);
  }
}
