package edu.buffalo.cse.cse486586.groupmessenger2;

class Message implements Comparable<Message>{
    String message;
    boolean isDeliverable;
    int sequenceNumber;
    int processId;
    String key;

    public Message(String message, int sequenceNumber, int processId){
        this.message = message;
        this.sequenceNumber = sequenceNumber;
        this.isDeliverable = false;
        this.processId = processId;
        this.key = this.sequenceNumber+"."+this.processId;
    }

    @Override
    public int compareTo(Message another) {
        if(this.sequenceNumber > another.sequenceNumber){
            return 1;
        }else if(this.sequenceNumber < another.sequenceNumber){
            return -1;
        }else{
            if(this.processId > another.processId){
                return 1;
            }else if(this.processId < another.processId){
                return -1;
            }else{
                return 0;
            }
        }
    }

    public void changeSequenceNumber(int sequenceNumber){
        this.sequenceNumber = sequenceNumber;
        this.key = this.sequenceNumber+"."+this.processId;
    }

    public void changeProcessId(int processId){
        this.processId = processId;
        this.key = this.sequenceNumber+"."+this.processId;
    }

}

