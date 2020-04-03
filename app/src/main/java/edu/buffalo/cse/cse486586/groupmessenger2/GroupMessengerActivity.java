package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.PriorityQueue;
import java.util.Random;

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
    static int LAST_ACCEPTED_MSG_SEQ = 0;
    static int LAST_PROPOSED_SEQ_NO = 0;
    static int MESSAGE_COUNT = 0;
    static PriorityQueue<Message> MSG_QUEUE;
    static int MY_PORT = -1;
    static Object lock = null;

    boolean serverStatus[] = {true, true, true, true, true};

    static void addRefresh(Message message){
        MSG_QUEUE.remove(message);
        MSG_QUEUE.add(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        Log.println(Log.DEBUG, "APP START", "onCreate has been called");
        GroupMessengerActivity.lock = new Object();
        //try {

            MSG_QUEUE = new PriorityQueue<Message>();

            TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
            MY_PORT = Integer.parseInt(portStr) * 2;

            // Create a server socket and open up an async task to listen for clients
            try {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                new ServerTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
                //Toast.makeText(this, "Server socket created! ", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                //Toast.makeText(this, "Can't create a server socket!", Toast.LENGTH_LONG).show();
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
        /*}catch(Exception exception){
            Log.println(Log.DEBUG, "_______DS app_____", exception.getMessage());
        }*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        GroupMessengerActivity groupMessengerActivity;

        ServerTask(GroupMessengerActivity groupMessengerActivity){
            this.groupMessengerActivity = groupMessengerActivity;
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri mUri = uriBuilder.build();

            ServerSocket serverSocket = sockets[0];
            Socket connectionSocket = null;
            Looper.prepare();
            Handler handler = new Handler();
            try {
                while(true)
                {
                    Log.println(Log.DEBUG, "Server", "Waiting for clients to connect");
                    connectionSocket = serverSocket.accept();
                    Log.println(Log.DEBUG, "Connected to a client", "asd");

                    // Spawn a new thread to handle the connection
                    Runnable r = new ClientHandler(this.groupMessengerActivity, connectionSocket, getContentResolver(), mUri);
                    //handler.post(r);
                    Thread th = new Thread(r);
                    th.start();
                }
            } catch (IOException e) {
                publishProgress(e.getMessage());
                e.printStackTrace();
                Log.println(Log.ERROR, "Server", e.getMessage());
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            Log.println(Log.DEBUG, "Server","Writing "+strings[0]+" to UI on server");
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\n");
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                // Create 5 client sockets and each connects to each of the avds server socket

                Random rand = new Random();
                int delay = rand.nextInt(200);
                Thread.sleep(delay);

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
                    try {
                        sockets[i].setSoTimeout(1200);
                        outputStreams[i] = new DataOutputStream(sockets[i].getOutputStream());
                        outputStreams[i].writeUTF(msgToSend);
                    }catch (Exception exception){
                        serverStatus[i] = false;
                        Log.println(Log.DEBUG, "Client: ", "Server "+i+": has failed. Msg: "+msgToSend);
                        Log.println(Log.DEBUG, "Client: ", "Exception info: "+exception.getMessage());
                        continue;
                    }
                    Log.println(Log.DEBUG, "Client: "+i,"Write message to server:"+msgToSend);
                }

                DataInputStream[] inputStreams = new DataInputStream[5];
                // get their proposals
                int bestProposalSeq = -1, bestProposalProcessId = -1;

                for (int i = 0; i < sockets.length; i++){
                    if(!serverStatus[i]){
                        continue;
                    }
                    String proposal = null;
                    try {
                        inputStreams[i] = new DataInputStream(sockets[i].getInputStream());
                        proposal = inputStreams[i].readUTF();
                    }catch(SocketTimeoutException exception){
                        serverStatus[i] = false;
                        Log.println(Log.DEBUG, "Client: ", "Server "+i+": has failed. Msg: "+msgToSend);
                        Log.println(Log.DEBUG, "Client: ", "Exception info: "+exception.getMessage());
                        continue;
                    }catch(StreamCorruptedException exception){
                        serverStatus[i] = false;
                        Log.println(Log.DEBUG, "Client: ", "Server "+i+": has failed. Msg: "+msgToSend);
                        Log.println(Log.DEBUG, "Client: ", "Exception info: "+exception.getMessage());
                        continue;
                    }catch (IOException exception){
                        serverStatus[i] = false;
                        Log.println(Log.DEBUG, "Client: ", "Server "+i+": has failed. Msg: "+msgToSend);
                        Log.println(Log.DEBUG, "Client: ", "Exception info: "+exception.getMessage());
                        continue;
                    }
                    Log.println(Log.DEBUG, "Client: ", "Proposal received from :"+i+": "+proposal+" for message: "+msgToSend);
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
                    if(!serverStatus[i]){
                        continue;
                    }
                    try {
                        outputStreams[i].writeUTF(bestProposal);
                    }catch (Exception exception){
                        serverStatus[i] = false;
                        Log.println(Log.DEBUG, "Client: ", "Server "+i+": has failed.");
                        Log.println(Log.DEBUG, "Client: ", "Exception info: "+exception.getMessage());
                        continue;
                    }
                    Log.println(Log.DEBUG, "Client:","Sending the best proposal to server :"+i+", Prop: "+bestProposal+", Msg: "+msgToSend);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostExceptions");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }

    public class MessageWriterOnUI extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            publishProgress(msgs[0]);
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            Log.println(Log.DEBUG, "Server","Writing "+strings[0]+" to UI on server");
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\n");
        }
    }

}




