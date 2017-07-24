package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by belstrel on 12.07.17.
 */
public class Server {
    //Статическое поле Map<String, server.Connection> connectionMap, где ключом будет имя клиента, а значением - соединение с ним
      private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

      public static void main(String[] args)  throws IOException{
       ConsoleHelper.writeMessage("Введите порт сервера: ");
       int serverPort = ConsoleHelper.readInt();
       try{
           ServerSocket serverSocket = new ServerSocket(serverPort);
           ConsoleHelper.writeMessage("Сервер запущен");
           while(true){
               //слушаем в бесконечном цикле
               Socket socket = serverSocket.accept();
               Handler handler = new Handler(socket);
               //запускаем хэндлер
               handler.start();
           }
       }catch(IOException e){
           System.out.println(e);
       }
    }

    private static class Handler extends Thread {
        private Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

         ConsoleHelper.writeMessage("Установлено соединение с адресом "+ socket.getRemoteSocketAddress());
         String clientName = null;
         //create connection
            try(
                    Connection connection = new Connection(socket)){
                //write message - connection accepted with remote address
                ConsoleHelper.writeMessage("Подключение к порту: "+connection.getRemoteSocketAddress());
                clientName = serverHandshake(connection);
                //send for all users info about name of new user
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, clientName));
                //send new user about other
                sendListOfUsers(connection, clientName);
                //run MAIN loop handling message by server
                serverMainLoop(connection, clientName);

            }catch(IOException e){
                ConsoleHelper.writeMessage("Ошибка при обмене данными с удаленным адресом");
            }catch(Exception e){
                ConsoleHelper.writeMessage("Ошибка при обмене даныыми с удаленным адресом");
            }
            //after handling all exception remove client  from map
            connectionMap.remove(clientName);
            //send message for other users
            sendBroadcastMessage(new Message(MessageType.USER_REMOVED, clientName));
            ConsoleHelper.writeMessage("Соединение с удаленным адресом закрыто");











        }

        //handshake
        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException{
        while(true){
            //сформировать и отправить команду запроса имени пользователя
            connection.send(new Message(MessageType.NAME_REQUEST));
            //получить ответ клиента
            Message message = connection.receive();
            //проверить что получена команда с именем пользователя
            if(message.getType() == MessageType.USER_NAME){
                //take name from message, chek it not null or empty
                if(message.getData() !=null && !message.getData().isEmpty()){
                    //and user with this name not connected
                    if(connectionMap.get(message.getData()) == null){
                        //add new user and his connection to map
                        connectionMap.put(message.getData(),connection);
                        //sent to user response that his name accepted
                        connection.send(new Message(MessageType.NAME_ACCEPTED));
                        //receive name as return value
                        return message.getData();
                    }
                }

            }
        }
        }

        //send list of all users
       private void sendListOfUsers(Connection connection, String userName) throws  IOException{
            for(String key : connectionMap.keySet()){
                Message message = new Message(MessageType.USER_ADDED, key);
                if(!key.equals(userName)){
                    connection.send(message);
                }
            }

       }
       //main loop handling message from server
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException{
            while(true){
            Message message = connection.receive();
            //if message type is text
            if(message.getType() == MessageType.TEXT){
                String s = userName + ": " + message.getData();

                Message formattedMessage = new Message(MessageType.TEXT, s);
                sendBroadcastMessage(formattedMessage);
            }else {
                ConsoleHelper.writeMessage("Error");
            }

            }
        }

    }
    //отправка сообщения всем пользователям
     public static void sendBroadcastMessage(Message message) {
         try {
             for (Connection connection : connectionMap.values()) {
                 connection.send(message);
             }
         } catch (Exception e) {
             e.printStackTrace();
             ConsoleHelper.writeMessage("Сообщение не отправлено");
         }
     }

}
