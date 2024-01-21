package com.java.crypto.Command.Commands;

import com.java.crypto.Client;

import com.java.crypto.Command.Action;
import com.java.crypto.Command.Commands.Parseable;
import com.java.crypto.Command.Sender;
import com.java.crypto.Packet.PACKET_TYPE;
import com.java.crypto.Packet.Packet;

public class ListAllClientsNamesOperation implements Action, Parseable{

    private static final String FLAG = "-l";

    private Sender sender;
    private Integer limit;

    public ListAllClientsNamesOperation(){}
    public ListAllClientsNamesOperation( Sender sender, String input ){ this.sender = sender; this.parse ( input );}

    @Override
    public void parse ( String input ) {

        String[] tokens = input.split ( " " );
        boolean isFlag  = false;
        Integer max     = null;
        
        for ( String token : tokens )
        {
            if ( token.contains ( FLAG ) ) isFlag = true;
            else if ( isFlag ) 
            {
                try {
                    max = Integer.parseInt ( token );
                }
                catch ( RuntimeException e ) { System.out.println( "[ERROR] you need to provide a valid number" ); }
            }
            else continue;
        }

        this.limit = max;

    }

    @Override
    public void execute() {

        String msg;
        PACKET_TYPE type = PACKET_TYPE.RESPONSE;

        if ( this.limit != null ) 
        {
            int limitValue = this.limit.intValue();
            msg            = limitValue + "," + Client.COMMANDS[2];
        }
        else {
            msg    = Client.COMMANDS[2];
        }

        Packet packet = new Packet(msg, type);
        this.sender.send(packet);
    }

}
