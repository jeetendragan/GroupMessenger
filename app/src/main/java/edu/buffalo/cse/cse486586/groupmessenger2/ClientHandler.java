package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.acl.Group;

public class ClientHandler implements Runnable {

    GroupMessengerActivity groupMessengerActivity;
    Socket connectionSocket;
    ContentResolver contentResolver;
    Uri mUri;

    public ClientHandler(GroupMessengerActivity groupMessengerActivity, Socket connectionSocket, ContentResolver contentResolver, Uri mUri){
        this.groupMessengerActivity = groupMessengerActivity;
        this.connectionSocket = connectionSocket;
        this.contentResolver = contentResolver;
        this.mUri = mUri;
    }

    @Override
    public void run() {
        try{
            connectionSocket.setSoTimeout(1200);
            DataInputStream inp = new DataInputStream(new BufferedInputStream(connectionSocket.getInputStream()));
            DataOutputStream out = new DataOutputStream(connectionSocket.getOutputStream());
            //Looper.prepare();
            // Step - 1 : get the message from the client
            String msg;
            try {
                 msg = inp.readUTF().trim();
            }catch(Exception e){
                Log.println(Log.DEBUG, "Server", "Some client has failed. ");
                this.groupMessengerActivity.runOnUiThread(new MessageDisplay(this.groupMessengerActivity, "Some client failed: "));
                return;
            }
            Log.println(Log.DEBUG, "Server", "Received message from client: "+msg);
            int nextProposedValue;
            Message message;

            synchronized (GroupMessengerActivity.lock) {
                // Step - 2: Look at the LAST_PROPOSED_SEQ_NO and LAST_ACCEPTED_MSG_SEQ and send the max of these + 1
                nextProposedValue = Math.max(GroupMessengerActivity.LAST_ACCEPTED_MSG_SEQ, GroupMessengerActivity.LAST_PROPOSED_SEQ_NO) + 1;
                GroupMessengerActivity.LAST_PROPOSED_SEQ_NO = nextProposedValue;

                message = new Message(msg, nextProposedValue, GroupMessengerActivity.MY_PORT);
                GroupMessengerActivity.MSG_QUEUE.add(message);


                // Step - 3: Propose this value. i.e. send this value to the client with my own identifier
                Log.println(Log.DEBUG, "Server", "Proposing a number to client: " + nextProposedValue + "." + GroupMessengerActivity.MY_PORT + " for msg: " + message.message);
                try {
                    out.writeUTF(nextProposedValue + "." + GroupMessengerActivity.MY_PORT);
                } catch (Exception exception) {
                    Log.println(Log.DEBUG, "Server", "Some client has failed.");
                    this.groupMessengerActivity.runOnUiThread(new MessageDisplay(this.groupMessengerActivity, "Some client failed: " + message.message));
                    synchronized (GroupMessengerActivity.lock) {
                        GroupMessengerActivity.MSG_QUEUE.remove(message);
                    }
                    return;
                }
                Log.println(Log.DEBUG, "Server", "Done proposing a number to client: " + nextProposedValue + "." + GroupMessengerActivity.MY_PORT + " for msg: " + message.message);
            }

            // wait for the client to send the proposal-accept with the next id
            // Step -4: The client has finalized the sequence number, and sends the proposal-accept
            String seqNum = null;
            try {
                seqNum = inp.readUTF().trim();
            }catch(Exception exception){
                Log.println(Log.DEBUG, "Server", "Some client has failed. Msg: "+message.message);
                this.groupMessengerActivity.runOnUiThread(new MessageDisplay(this.groupMessengerActivity, "Some client failed: "+message.message));
                synchronized (GroupMessengerActivity.lock)
                {
                    GroupMessengerActivity.MSG_QUEUE.remove(message);
                }
                return;
            }
            Log.println(Log.DEBUG, "Server", "Server has received a seq no for msg: "+message.message+" = "+seqNum);
            String[] seqComp = seqNum.split("\\.");
            int newMessageSequence = Integer.parseInt(seqComp[0]);
            int processIdComp = Integer.parseInt(seqComp[1]);
            synchronized (GroupMessengerActivity.lock)
            {
                GroupMessengerActivity.LAST_ACCEPTED_MSG_SEQ = newMessageSequence;

                message.changeSequenceNumber(newMessageSequence);
                message.changeProcessId(processIdComp);
                message.isDeliverable = true;

                // step 5 - Update the queue with the updated process
                GroupMessengerActivity.addRefresh(message);

                // step 6 - Deliver all the messages at the head of the queue that are deliverable
                Message topMsg = null;
                while ((topMsg = GroupMessengerActivity.MSG_QUEUE.peek()) != null && topMsg.isDeliverable) {
                    // remove the top message from the queue and deliver it
                    Message deliverableMsg = GroupMessengerActivity.MSG_QUEUE.poll();
                    ContentValues cv = new ContentValues();
                    cv.put("key", GroupMessengerActivity.MESSAGE_COUNT);
                    GroupMessengerActivity.MESSAGE_COUNT++;
                    cv.put("value", deliverableMsg.message);
                    this.contentResolver.insert(this.mUri, cv);
                    Log.println(Log.DEBUG, "Server", "Message delivered by server:" + deliverableMsg.message+" Id: "+topMsg.sequenceNumber+", "+topMsg.processId);
                    //publishProgress(deliverableMsg.key+": "+deliverableMsg.message);
                    this.groupMessengerActivity.runOnUiThread(new MessageDisplay(this.groupMessengerActivity, deliverableMsg.key+": "+deliverableMsg.message));
                }
            }
        } catch (IOException e) {
            //publishProgress(e.getMessage());
            e.printStackTrace();
            Log.println(Log.ERROR, "Server", e.getMessage());
        }
    }
}
