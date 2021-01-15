package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler {

    Server server = null;
    Socket socket = null;
    DataInputStream in;
    DataOutputStream out;
    private String nickname;
    private String login;
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    Handler fileHandler;

    {
        try {
            fileHandler = new FileHandler("log_%g", 10*1024, 20, true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public ClientHandler(Server server, Socket socket) {
        logger.addHandler(fileHandler);
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            ///==============///
//            new Thread(
            server.getExecutorService().execute(
            ///==============///
            ()-> {
                    try {
                        // цикл аутентификации
                        while (true){
                            socket.setSoTimeout(12000);
                            String str = in.readUTF();

                            if (str.startsWith("/auth")){
                                String[] token = str.split("\\s");
                                String newNick = server.getAuthService().getNicknameByLoginAndPassword(token[1], token[2]);
                                login = token[1];

                                if (newNick != null){
                                    if(!server.isLoginAuthenticated(token[1])) {
                                        nickname = newNick;
                                        sendMsg("/authok " + nickname);
                                        server.subscribe(this);
                                        //System.out.println("Клиент " + nickname + " подключился");
                                        logger.log(Level.INFO, "Клиент " + nickname + " подключился");
                                        socket.setSoTimeout(0);
                                        break;
                                    }else{
                                        sendMsg("С данной учетной записью уже зашли");
                                    }
                                }else {
                                    sendMsg("Неверный логин / пароль");
                                }
                            }

                            if (str.startsWith("/reg")){
                                String[] token = str.split("\\s");
                                logger.log(Level.INFO, "Клиент пытается зарегистрироваться");
                                if(token.length < 4){
                                    continue;
                                }
                                boolean isRegistration = server.getAuthService()
                                        .registration(token[1], token[2], token[3]);
                                if(isRegistration){
                                    sendMsg("/regok");
                                } else {
                                    sendMsg("/regno");
                                }
                            }
                        }

                        // цикл работы
                        while (true) {
                            String str = in.readUTF();

                            if(str.startsWith("/")) {

                                if (str.equals("/end")) {
                                    out.writeUTF("/end");
                                    break;
                                }

                                if (str.startsWith("/w")) {
                                    String[] token = str.split("\\s+", 3);
                                    if (token.length <3){
                                        continue;
                                    }
                                    logger.log(Level.INFO, "Клиент " + nickname + " отправляет личное сообщение");
                                    server.privateCastMsg(this, token[1], token[2]);
                                }

                                if (str.startsWith("/chnick ")) {
                                    String[] token = str.split(" ", 2);
                                    if (token.length < 2) {
                                        continue;
                                    }
                                    if (token[1].contains(" ")) {
                                        sendMsg("Ник не может содержать пробелов");
                                        continue;
                                    }
                                    if (server.getAuthService().changeNick(this.nickname, token[1])) {
                                        sendMsg("/yournickis " + token[1]);
                                        sendMsg("Ваш ник изменен на " + token[1]);
                                        logger.log(Level.INFO, "Клиент " + nickname + " сменил ник на " + token[1]);
                                        this.nickname = token[1];
                                        server.broadClientList();

                                    } else {
                                        sendMsg("Не удалось изменить ник. Ник " + token[1] + " уже существует");
                                    }
                                }

                            }else {
                                server.broadCastMsg(this, str);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        //System.out.println("Клиент отключился");
                        logger.log(Level.INFO, "Клиент отключился");
                        server.unsubscribe(this);
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            ///==============///
            });
//                    .start();
            ///==============///

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname(){
        return nickname;
    }

    public String getLogin() {
        return login;
    }
}
