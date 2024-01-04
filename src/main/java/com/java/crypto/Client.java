package com.java.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.util.Scanner;

import com.java.crypto.Command.Action;
import com.java.crypto.Command.Sender;
import com.java.crypto.Command.Commands.ExitChatApplicationOperation;
import com.java.crypto.Command.Commands.ListAllClientsNamesOperation;
import com.java.crypto.Command.Commands.PingServerOperation;
import com.java.crypto.Command.Commands.ShowServerInfoOperation;
import com.java.crypto.Encryption.Utils;
import com.java.crypto.Packet.PACKET_TYPE;
import com.java.crypto.Packet.Packet;

public class Client {

    private static String[] COMMANDS = {
        "ping", "server_info", "list", "exit"
    };

    // ------
    // for now let's make the P and G values client-side
    private static BigInteger G = new BigInteger ( "20394481638768721056615848876245598348394903299043512852492281760503316714808291665302963293455165435328506888086435404404204632685585470908913141798575140530780856974512234232530155934475327731191658220519670965412573785175625893014209933427938689925569907013354427154431624696530852180230775370974689434433815417554854233263628116425767063740417782648086583522665480104542298920279131195529379624127918190845477758723837863530632451048844815335086878889738979918045548634724983765815120421312220502491387695531657592871110472257658801419561376253535416234821844601342702427241532924853197883071656885799388030677861" );
    private static BigInteger P = new BigInteger ( "3922787691195947410766974945019347107439752388145898685327442220462710906475649215861159795786692275911200172490281567649851267962773991026596149308442384877945142252566605073114683509635966897286559433805573069750668355165443125270669650248092499512172556805065727300638974213319098980285092339177322601555006523340428654254701511913407472165729850873422521929834133284123380151545138162053956478318412187333871073328348593216727641652139164739088544012855940040864541624003035707173971125255153366118272044892276407899754962569575445744761523723247453110394487732289019202883054304496302883362783045814754329274497");

    // convert them to the adequate class when doing the rsa encryption
    private BigInteger pk;
    private BigInteger sk;
    // ------

    private static char COMMAND_DELIMITER = '/';
    private static String DEFAULT_HOST = "localhost";
    private static final Scanner scanner = new Scanner(System.in);

    private Socket socket;
    private String name;
    // this is what rsa can decrypt;
    private static int MAX_BYTE_SIZE = 245;
    private static int MAX_SIZE_KEY_BIT = 257;

    private int msgLength = MAX_SIZE_KEY_BIT;

    // channel where you can read from
    private InputStream is;
    // chanel where you can write from
    private OutputStream os;

    // this is essential to the command pattern
    private Sender sender;

    public Client(){}
    public Client ( String name, int port )
    {
        try {
            this.name=name;
            this.socket = new Socket(DEFAULT_HOST, port);
            this.socket.setTcpNoDelay(true);

            is = socket.getInputStream();
            os = socket.getOutputStream();

            sender = new Sender(os);
        
            // generate the pk only !
            // the sk will be computed thanks to the diffie hellman exchange.
            System.out.println("[CLIENT] public key with length 2048 is being generated ...");
            this.pk = Utils.generateBigPrime(); 

            mainLoop();
        }catch( IOException err ) {
            exitAppOnServerShutDown();
        }
    }

    private void sendKeyPKMixed ( )
    {
        try{
            // you broadcast your key thanks
            // bfore even sending your name
            System.out.println("[CLIENT] sending the mixed key to the server.");
            BigInteger mixKey   = Utils.mixKey(this.pk, G, P);
            byte[] bytes        = mixKey.toByteArray();

            Packet packet = new Packet(bytes, PACKET_TYPE.KEY);
            byte[] bb     = packet.output();

            // sending the content of the packet 
            os.write(bb);
            os.flush();

        }catch ( IOException e ) { e.printStackTrace(); }
    }

    private void sendNamePacket ( )
    {
        System.out.println("[CLIENT] sending the name to the server and other parties.");
        Packet SecondDefaultPacket = new Packet(name, PACKET_TYPE.CONNECT);
        sendPacket(SecondDefaultPacket);
    }


    // this the main loop
    // think of it like the main Game Loop
    public void mainLoop()
    {

        // firstly, we notify the server of our name,
        // using the CONNECT packet
        sendKeyPKMixed();

        receivePacket();

        // then when the key exchange is finished, send the username.
        sendNamePacket();

        inputTask();

        while( this.socket.isConnected() )
        {
            receivePacket();
        }
    }

    // asks, while the program is running the input of the user
    public void inputTask ( )
    {
        new Thread( new Runnable() {
            @Override
            public void run()
            {
                String command = "";
                Action command_ ;
                boolean running = true;
                String msg      = name + " >";
                String input    = "";
                Packet packet   ;

                while ( running )
                {
                    System.out.print(msg); 
                    input = scanner.nextLine();

                    // on Input check if there's any command
                    // a command starts with the COMMAND_DELIMITER

                    if ( input.isEmpty() ) { System.out.println("[ERROR] you need to say something, everyone is waiting ..."); }
                    else
                    {
                        if ( input.charAt(0) == COMMAND_DELIMITER )
                        {
                            command = input.substring(1);
                            if ( command.equals( COMMANDS[0]) )
                            {
                                command_ = new PingServerOperation(sender);
                                command_.execute();
                            }
                            else if ( command.equals(COMMANDS[1]) )
                            {
                                command_ = new ShowServerInfoOperation( sender );
                                command_.execute();
                            }
                            else if ( command.equals(COMMANDS[2]) )
                            {
                                command_ = new ListAllClientsNamesOperation( sender );
                                command_.execute();
                            }
                            else if ( command.equals( COMMANDS[3]) )
                            {
                                command_ = new ExitChatApplicationOperation( sender) ;
                                command_.execute();
                            }
                            else 
                            {
                                // TODO implement the lev algo
                                // String diff
                                System.out.println("[CLIENT, ERROR] this command doesn't exist !");
                            }
                        }
                        else {
                            // by default, we broadcast each msg
                            packet = new Packet(input, PACKET_TYPE.SEND);
                            sendPacket ( packet );
                        }
                    }
                }
            }
        }).start();
    }

    // we don't need to handle this anymore.
    // private void receivePacketLength ()
    // {
    //     try {
    //         this.msgLength = is.read();
    //     }        
    //     catch ( IOException e ){ 
    //         System.err.println("Couldn't read the msg from the server");    
    //     }
    // }

    // but is this a blocking line ?, well yeah...
    // we should maybe have a thread for writing and another thread for inputing
    private void receivePacket( )
    {
        Packet packet;

        try {

            byte[] allocateByteMsgArray = new byte[this.msgLength];
            is.read(allocateByteMsgArray);

            // do nothing if the byte read is zero
            packet  = new Packet( allocateByteMsgArray );

            System.out.println("received packet with info : " + packet);

            switch (packet.getType()) {
                case RESPONSE:
                    // RESPONSE enum means it's a broacast
                    System.out.println( packet.getMsg() );
                    break;
                
                case DISCONNECT:
                    System.out.println( packet.getMsg() );
                    break;

                case KEY: // we receive the key from the other user
                    // which in theory should be the mixed key
                    // we should take that mixed key and put modPow it.
                    setSK( packet );
                    break;

                default:
                    break;
            }

        }catch ( IOException e ){ 
            System.err.println("[CLIENT] couldn't read the msg from the server");    
        }
    }


    // sets the private key
    private void setSK ( Packet packet ) 
    {
        System.out.println("[CLIENT] setting new private key.");
        this.sk = Utils.gSK ( this.pk, new BigInteger ( packet.getMsg_ () ), Client.P ); 
    }

    // b4 sending any packet, we send it's length through the socket
    // private void sendPacketLength ( Packet packet ){
    //     try{

    //         // get the length of the byte array
    //         // if it is the key, then we can allow it to pass
    //         // since we don't rsa the key.
    //         int lengthMsgRaw = packet.getMsg().length();
    //         int lengthByteMsgToSend = packet.output().length;
    //         if ( lengthMsgRaw > 245 ) { System.out.println( "ERROR, due to the length of your secret key ( 2048 ), your messages shall not exceed 245 character" ); return; };
    //         os.write(lengthByteMsgToSend);
    //         os.flush();

    //     }catch ( IOException e ) { 
    //         exitAppOnServerShutDown();
    //     } 
    // }

    // the client can : SEND_PACKETS ( MSG to the server );
    private void sendPacket ( Packet packet )
    {
        try {
            // the flush occurs when a new line operator is entered
            // we simply call the flush method;
            // wiich propably less efficient
            // TODO : change.
            os.write(packet.output());
            os.flush();
        }
        catch ( IOException e )
        { 
            // if we're here, then the server must have shut down.
            // we show to the user that the server shut down.
            // exit the application with status 1 ( error )
            exitAppOnServerShutDown();
       }
    }

    // static, so that this function can be called inside static context
    private static void exitAppOnServerShutDown()
    {
        String msg = "The server couldn't take it anymore...";
        System.out.println(msg);
        System.exit( 1 );
    }
}
