package client;

import server.Connection;
import server.ConsoleHelper;
import server.Message;
import server.MessageType;

import java.io.IOException;
import java.net.Socket;

import static server.MessageType.*;

/**
 * Created by belstrel on 13.07.17.
 */
public class Client {
    protected Connection connection;
    private volatile boolean clientConnected= false;

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }


    public void run(){
        SocketThread socketThread = getSocketThread();
        socketThread.setDaemon(true);
        socketThread.start();
        //ожидаем нотификацию с другого потока
        try{
            synchronized (this){
                this.wait();
            }
        }catch(InterruptedException e){
            ConsoleHelper.writeMessage("Ошибка");
            return;
        }
        //после нотификации проверям значение clientConnected
        if(clientConnected){
            ConsoleHelper.writeMessage("Соединение установлено.Для выхода наберите команду 'exit'.");
            while(clientConnected){
                String message;
                if(!(message = ConsoleHelper.readString()).equals("exit")){
                    if(shouldSentTextFromConsole()){
                        sendTextMessage(message);
                    }
                }else{
                    return;
                }
            }
        }
        else{
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента.");
        }
    }

    protected String getServerAddress(){
        ConsoleHelper.writeMessage("Введите адрес сервера: ");
        return ConsoleHelper.readString();
    }
    protected 	int getServerPort(){
        ConsoleHelper.writeMessage("Введите порт сервера: ");
        return ConsoleHelper.readInt();

    }
    protected String getUserName(){
        ConsoleHelper.writeMessage("Введите имя пользователя: ");
        return ConsoleHelper.readString();
    }
    protected boolean shouldSentTextFromConsole(){
        return true;
    }
    protected SocketThread getSocketThread(){
        return   new SocketThread();

    }
    protected void sendTextMessage(String text){
       try{
           connection.send(new Message(MessageType.TEXT, text));
       }catch(IOException e){
          ConsoleHelper.writeMessage("Ошибка отправки");
          clientConnected = false;
       }
    }


    public class SocketThread extends Thread{
        protected void processIncomingMessage(String message){
            ConsoleHelper.writeMessage(message);
        }
        /** должен выводить в консоль информацию о том, что участник с именем userName присоединился к чату **/
        protected void informAboutAddingNewUser(String userName) {
            ConsoleHelper.writeMessage("участник " + userName + " присоединился к чату");
        }
        /**  должен выводить в консоль, что участник с именем userName покинул чат **/
        protected void informAboutDeletingNewUser(String userName) {
            ConsoleHelper.writeMessage("участник " + userName + " покинул чат");
        }


        /** Устанавливать значение поля clientConnected класса Client в соответствии с
         переданным параметром.
         Оповещать (пробуждать ожидающий) основной поток класса Client **/
        protected void notifyConnectionStatusChanged(boolean clientConnected) {


            Client.this.clientConnected = clientConnected;

            synchronized (Client.this) {
                Client.this.notify();
            }
        }

        //main loop handling message from server
        protected  void clientMainLoop() throws IOException, ClassNotFoundException{
            while(true){
                //get message from server using connection
                Message message = connection.receive();

                switch(message.getType()) {
                    //if type is TEXT handling message using method processIncomingMessage()
                    case TEXT: {
                        processIncomingMessage(message.getData());
                        break;
                    }

                    case USER_ADDED: {
                        informAboutAddingNewUser(message.getData());
                        break;
                    }
                    case USER_REMOVED:{
                        informAboutDeletingNewUser(message.getData());
                        break;
                    }
                    default:
                        throw new IOException("Unexpected MessageType");

                }

            }
        }
        protected void clientHandshake() throws IOException, ClassNotFoundException{
            while(true){
                //receive message in loop, using connection
                Message message = connection.receive();
                switch (message.getType()){
                    //if message type is NAME_REQUEST
                    case NAME_REQUEST: {
                       //request to enter a name using method getUserName()
                        //create new message with type USER_NAME and insert name, send message to server
                         String userName = getUserName();
                         connection.send(new Message(USER_NAME, userName));
                         break;
                    }
                    //if message type is NAME_ACCEPTED
                    case NAME_ACCEPTED:{
                        // значит сервер принял имя клиента, нужно об этом сообщить главному потоку, он этого очень ждет.
                        // Сделай это с помощью метода notifyConnectionStatusChanged(), передав в него true. После этого выйди из метода.
                          notifyConnectionStatusChanged(true);
                          return;

                    }
                    default: {
                        throw new IOException("Unexpected MessageType");
                    }

                    }
            }
        }
        public void run(){
//create new object socket with request server and port
            try {
                Socket socket = new Socket(getServerAddress(), getServerPort());
                Client.this.connection = new Connection(socket);

                clientHandshake();
                clientMainLoop();

            } catch (IOException e) {
                notifyConnectionStatusChanged(false);
            } catch (ClassNotFoundException e) {
                notifyConnectionStatusChanged(false);
            }

        }
    }

}
