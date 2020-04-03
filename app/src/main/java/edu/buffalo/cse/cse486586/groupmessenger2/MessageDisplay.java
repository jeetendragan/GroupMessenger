package edu.buffalo.cse.cse486586.groupmessenger2;

import android.widget.TextView;

public class MessageDisplay implements Runnable {

    GroupMessengerActivity groupMessengerActivity;
    String message;

    MessageDisplay(GroupMessengerActivity groupMessengerActivity, String message){
        this.groupMessengerActivity = groupMessengerActivity;
        this.message = message;
    }

    @Override
    public void run() {
        TextView messageTxtView = (TextView) this.groupMessengerActivity.findViewById(R.id.textView1);
        messageTxtView.append("\n"+this.message);
    }
}
