package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT =      10000;

    // these are the variables used by all threads on the Server
    // client does not need to use them
    static int LAST_ACCEPTED_MSG_SEQ = -1;
    static int LAST_PROPOSED_SEQ_NO = 0;
    static PriorityQueue<Message> MSG_QUEUE;
    static int MY_PORT = -1;

    static void addRefresh(Message message){
        MSG_QUEUE.remove(message);
        MSG_QUEUE.add(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        MSG_QUEUE = new PriorityQueue<Message>();

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        MY_PORT = Integer.parseInt(portStr) * 2;

        // Create a server socket and open up an async task to listen for clients
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            Toast.makeText(this, "Server socket created! ", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Can't create a server socket!", Toast.LENGTH_LONG).show();
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.editText1);
                String msg = editText.getText().toString();
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
                editText.setText("");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri mUri = uriBuilder.build();

            ServerSocket serverSocket = sockets[0];
            Socket connectionSocket = null;
            DataInputStream inp = null;
            DataOutputStream out = null;
            try {
                while(true)
                {
                    Log.println(Log.DEBUG, "Server", "Waiting for clients to connect");
                    connectionSocket = serverSocket.accept();
                    Log.println(Log.DEBUG, "Connected to a client", "asd");

                    inp = new DataInputStream(new BufferedInputStream(connectionSocket.getInputStream()));
                    out = new DataOutputStream(connectionSocket.getOutputStream());

                    // Step - 1 : get the message from the client
                    String msg = inp.readUTF().trim();

                    // Step - 2: Look at the LAST_PROPOSED_SEQ_NO and LAST_ACCEPTED_MSG_SEQ and send the max of these + 1
                    int nextProposedValue = Math.max(LAST_ACCEPTED_MSG_SEQ, LAST_PROPOSED_SEQ_NO) + 1;
                    LAST_PROPOSED_SEQ_NO = nextProposedValue;

                    Message message = new Message(msg, nextProposedValue, MY_PORT);
                    MSG_QUEUE.add(message);

                    // Step - 3: Propose this value. i.e. send this value to the client with my own identifier
                    out.writeUTF(nextProposedValue+"."+MY_PORT);

                    // wait for the client to send the proposal-accept with the next id
                    // Step -4: The client has finalized the sequence number, and sends the proposal-accept
                    String seqNum = inp.readUTF().trim();
                    String[] seqComp = seqNum.split("\\.");
                    int newMessageSequence = Integer.parseInt(seqComp[0]);
                    int processIdComp = Integer.parseInt(seqComp[1]);
                    message.changeSequenceNumber(newMessageSequence);
                    message.changeProcessId(processIdComp);
                    message.isDeliverable = true;

                    // step 5 - Update the queue with the updated process
                    addRefresh(message);

                    // step 6 - Deliver all the messages at the head of the queue that are deliverable
                    Message topMsg = null;
                    while( (topMsg = MSG_QUEUE.peek()) != null && topMsg.isDeliverable)
                    {
                        // remove the top message from the queue and deliver it
                        Message deliverableMsg = MSG_QUEUE.poll();
                        ContentValues cv = new ContentValues();
                        cv.put("key", deliverableMsg.key);
                        cv.put("value", deliverableMsg.message);
                        ContentResolver contentResolver = getContentResolver();
                        contentResolver.insert(mUri, cv);
                        Log.println(Log.DEBUG, "Server","Message delivered by server:"+deliverableMsg.message);
                        publishProgress(deliverableMsg.key+": "+deliverableMsg.message);
                    }
                }
            } catch (IOException e) {
                publishProgress(e.getMessage());
                e.printStackTrace();
                Log.println(Log.DEBUG, "Server", e.getMessage());
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            //Toast.makeText(SimpleMessengerActivity.this, strings[0], Toast.LENGTH_SHORT).show();
            Log.println(Log.DEBUG, "Server","Writing "+strings[0]+" to UI on server");
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             *

            String filename = "SimpleMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }*/

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                // Create 5 client sockets and each connects to each of the avds server socket
                Socket sockets[] = new Socket[5];

                sockets[0] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(GroupMessengerActivity.REMOTE_PORT0));

                sockets[1] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(GroupMessengerActivity.REMOTE_PORT1));

                sockets[2] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(GroupMessengerActivity.REMOTE_PORT2));

                sockets[3] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(GroupMessengerActivity.REMOTE_PORT3));

                sockets[4] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(GroupMessengerActivity.REMOTE_PORT4));

                String msgToSend = msgs[0];

                DataOutputStream[] outputStreams = new DataOutputStream[5];
                // send messages to all the servers
                for (int i = 0; i < sockets.length; i++){
                    outputStreams[i] = new DataOutputStream(sockets[i].getOutputStream());
                    outputStreams[i].writeUTF(msgToSend);
                    Log.println(Log.DEBUG, "Client: "+i,"Write message to server:"+msgToSend);
                }

                DataInputStream[] inputStreams = new DataInputStream[5];
                // get their proposals
                int bestProposalSeq = -1, bestProposalProcessId = -1;
                for (int i = 0; i < sockets.length; i++){
                    inputStreams[i] = new DataInputStream(sockets[i].getInputStream());
                    String proposal = inputStreams[i].readUTF();
                    Log.println(Log.DEBUG, "Client: ", "Proposal received from :"+i+": "+proposal);
                    String[] proposalParts = proposal.split("\\.");
                    int proposalSeq = Integer.parseInt(proposalParts[0]);
                    int proposalProcessId = Integer.parseInt(proposalParts[1]);

                    if(bestProposalSeq == -1 && bestProposalProcessId == -1){
                        bestProposalProcessId = proposalProcessId;
                        bestProposalSeq = proposalSeq;
                    }else{
                        if(bestProposalSeq < proposalSeq){
                            bestProposalSeq = proposalSeq;
                            bestProposalProcessId = proposalProcessId;
                        }else if(bestProposalSeq == proposalSeq){
                            if(bestProposalProcessId < proposalProcessId){
                                bestProposalSeq = proposalSeq;
                                bestProposalProcessId = proposalProcessId;
                            }
                        }
                    }

                }

                // We have the best proposal now
                // send the proposal to all the servers
                String bestProposal = bestProposalSeq+"."+bestProposalProcessId;
                for (int i = 0; i < sockets.length; i++){
                    outputStreams[i].writeUTF(bestProposal);
                    Log.println(Log.DEBUG, "Client:","Sending the best proposal to all servers:"+bestProposal);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }

}




