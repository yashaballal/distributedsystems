package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

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
    static int processID;

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private static int globalSeqNum=0;

    static final int SERVER_PORT = 10000;

    private Uri mUri ;

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);

        return uriBuilder.build();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        if(myPort.equals("11108"))
        {
            processID = 1;
        }
        else if(myPort.equals("11112"))
        {
            processID = 2;
        }
        else if(myPort.equals("11116"))
        {
            processID = 3;
        }
        else if(myPort.equals("11120"))
        {
            processID = 4;
        }
        else if(myPort.equals("11124"))
        {
            processID = 5;
        }


        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */

            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(SERVER_PORT));
            new ServerTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            e.printStackTrace();
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        /*YASHA comments: You need to use the tv.append() method to attach*/
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        final EditText editText = (EditText) findViewById(R.id.editText1);

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
        findViewById(R.id.button4).setOnClickListener(new OnSendClickListener(tv, getContentResolver(), editText));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class OnSendClickListener implements View.OnClickListener
    {
        private static final String KEY_FIELD = "key";
        private static final String VALUE_FIELD = "value";

        private final TextView mTextView;
        private final ContentResolver mContentResolver;
        private final Uri mUri;
        private EditText editText;


        public OnSendClickListener(TextView _tv, ContentResolver _cr, EditText editText) {
            mTextView = _tv;
            mContentResolver = _cr;
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            this.editText = editText;
        }
        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }



        @Override
        public void onClick(View v) {
            String message = editText.getText().toString();
            editText.setText("");
            new ClientTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
        }

    }

    class Msg{
        String proposedSeqNo;
        String msg;
        boolean deliverable;
        long millitime;

        Msg(String proposedSeqNo, String msg, boolean deliverable, long millitime)
        {
            this.proposedSeqNo = proposedSeqNo;
            this.msg = msg;
            this.deliverable = deliverable;
            this.millitime = millitime;

        }
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        /*To handle ConcurrentModificationException, used this datastructure*/
        CopyOnWriteArrayList<Msg> l1 = new CopyOnWriteArrayList<Msg>();
        Date date = new Date();
        int localSeqNo = 0;
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            Socket aSocket = null;
            int iter = 0;
            try {
                while (true) {

                    aSocket = serverSocket.accept();

                    //aSocket.setSoTimeout(1500);
                    /*Usage of DataInputStream
                    Reference:
                    https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                    https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                    DataInputStream dataInputStream = new DataInputStream(aSocket.getInputStream());
                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());
                    String message;
                    try {
                        message = dataInputStream.readUTF();
                        for(Msg obj : l1)
                        {
                            if(date.getTime()-obj.millitime > 1000)
                            {
                                l1.remove(obj);
                                break;
                            }
                        }

                        if(message.length()>4 && message.substring(0,4).equals("SEQ:"))
                        {
                            boolean flag = false;
                            String strArray[] = message.split(" ");
                            if(localSeqNo < (int) Double.parseDouble(strArray[2]))
                            {
                                localSeqNo = (int) Double.parseDouble(strArray[2]);
                            }
                            int iter1 = 0;
                            int mainiter = 0;
                            while (mainiter < l1.size()) {

                                Msg temp = l1.get(mainiter);
                                if (temp.proposedSeqNo.equals(strArray[1]))
                                {

                                    l1.remove(mainiter);

                                    if(l1.size() == 0)
                                    {
                                        l1.add(new Msg(strArray[2], temp.msg, true, date.getTime()));
                                    }
                                    else
                                    {
                                        for (Msg obj : l1)
                                        {
                                            if (Double.parseDouble(obj.proposedSeqNo) > Double.parseDouble(strArray[2]))
                                            {
                                                l1.add(iter1, new Msg(strArray[2], temp.msg, true, date.getTime()));
                                                flag = true;
                                                break;
                                            }
                                            iter1++;
                                        }

                                        if(flag == false)
                                        {
                                            l1.add(new Msg(strArray[2], temp.msg, true, date.getTime()));
                                        }

                                    }

                                    for (Msg obj : l1)
                                    {
                                        if (obj.deliverable == true )
                                        {
                                            l1.remove(obj);
                                            ContentValues cv = new ContentValues();
                                            cv.put(KEY_FIELD, String.valueOf(globalSeqNum));
                                            cv.put(VALUE_FIELD, obj.msg);
                                            getContentResolver().insert(mUri, cv);
                                            publishProgress(String.valueOf(globalSeqNum));
                                            globalSeqNum++;
                                        }
                                        else
                                        {
                                            break;
                                        }

                                    }
                                    dataOutputStream.writeUTF("ACK");
                                    dataOutputStream.flush();


                                }
                                mainiter++;
                            }
                        }
                        else
                        {
                            localSeqNo++;
                            boolean flag = false;
                            double valueComp = localSeqNo + 0.1 * processID;
                            int iter1 = 0;
                            if(l1.size()==0)
                            {
                                l1.add(new Msg(String.valueOf(valueComp), message, false, date.getTime()));
                            }
                            else {
                                for (Msg obj : l1) {

                                    if (Double.parseDouble(obj.proposedSeqNo) > valueComp) {
                                        l1.add(iter1, new Msg(String.valueOf(valueComp), message, false, date.getTime()));
                                        flag = true;
                                        break;
                                    }
                                    iter1++;
                                }
                                if(flag == false)
                                {
                                    l1.add(new Msg(String.valueOf(valueComp), message, false, date.getTime()));
                                }
                            }


                            /*Sending ACK to client indicating that the message has been published in server
                             * machine
                             * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/

                            dataOutputStream.writeUTF(String.valueOf(valueComp));
                            dataOutputStream.flush();
                        }


                        /*Usage of DataOutputStream
                        Reference:
                        https://www.geeksforgeeks.org/dataoutputstream-in-java/
                        https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/

                        dataOutputStream.close();
                        dataInputStream.close();
                    }
                    catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    }
                    catch(SocketTimeoutException e)
                    {
                        e.printStackTrace();
                    }
                    catch(StreamCorruptedException e)
                    {
                        e.printStackTrace();
                    }
                    catch(EOFException e)
                    {
                        e.printStackTrace();
                    }
                    catch(FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "ClientTask socket IOException");
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }

                }

            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            finally {
                try
                {
                    /*
                     * Closing the socket in a finally block so that it gets executed even if there is
                     * an exception
                     * */
                    aSocket.close();
                    serverSocket.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
            return null;
        }


        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            Cursor resultCursor = getContentResolver().query(mUri, null, strReceived, null, null);
            int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
            resultCursor.moveToFirst();

            String returnValue = resultCursor.getString(valueIndex);
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(returnValue + "\t\n");

            String filename = "GroupMessengerOutput";
            String string = returnValue + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }

            return;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        double proposedSeqDouble = 0;

        @Override
        protected Void doInBackground(String... msgs) {
            Socket socket0 = null;
            Socket socket1 = null;
            Socket socket2 = null;
            Socket socket3 = null;
            Socket socket4 = null;

            DataOutputStream dataOutputStream = null;
            try {

                socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT0));
                socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT1));
                socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT2));
                socket3 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT3));
                socket4 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT4));

                //Reference: https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#setSoTimeout(int)
                socket0.setSoTimeout(1000);
                socket1.setSoTimeout(1000);
                socket2.setSoTimeout(1000);
                socket3.setSoTimeout(1000);
                socket4.setSoTimeout(1000);

            }
            catch(Exception e) {
                e.printStackTrace();
            }
            double processIdOfResp[] = new double[5];
            String message = msgs[0];
                /*Usage of DataOutputStream
                Reference:
                https://www.geeksforgeeks.org/dataoutputstream-in-java/
                https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
            Socket tempSocket = null;
            for(int i =0; i<=4; i++) {
                try
                {
                    if(i==0)
                        tempSocket = socket0;
                    else if(i==1)
                        tempSocket = socket1;
                    else if(i==2)
                        tempSocket = socket2;
                    else if(i==3)
                        tempSocket = socket3;
                    else if(i==4)
                        tempSocket = socket4;


                    dataOutputStream = new DataOutputStream(tempSocket.getOutputStream());
                    dataOutputStream.writeUTF(message);
                    dataOutputStream.flush();
                    /* Usage of DataInputStream
                     * Reference:
                     * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                     * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
                     * */

                    /*
                     * Receiving ACK from server side indicating that the message has been received
                     * and published to the server machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets
                     * */
                    DataInputStream dataInputStream = new DataInputStream(tempSocket.getInputStream());
                    String proposedSeqStr = dataInputStream.readUTF();

                    int num = Integer.parseInt(proposedSeqStr.substring(proposedSeqStr.lastIndexOf(".")+1));
                    //System.out.println("proposedSeqStr "+proposedSeqStr);
                    //System.out.println("The value of num is: "+ num);
                    processIdOfResp[num-1] = Double.parseDouble(proposedSeqStr);

                    if(Double.parseDouble(proposedSeqStr) > proposedSeqDouble)
                    {
                        proposedSeqDouble = Double.parseDouble(proposedSeqStr);
                    }

                    dataInputStream.close();
                    dataOutputStream.close();
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch(SocketTimeoutException e)
                {
                    e.printStackTrace();
                }
                catch(StreamCorruptedException e)
                {
                    e.printStackTrace();
                }
                catch(EOFException e)
                {
                    e.printStackTrace();
                }
                catch(FileNotFoundException e)
                {
                    e.printStackTrace();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ClientTask socket IOException");
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }

            }

            try {

                socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT0));
                socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT1));
                socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT2));
                socket3 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT3));
                socket4 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(REMOTE_PORT4));

                //Reference: https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#setSoTimeout(int)
                socket0.setSoTimeout(1000);
                socket1.setSoTimeout(1000);
                socket2.setSoTimeout(1000);
                socket3.setSoTimeout(1000);
                socket4.setSoTimeout(1000);

            }
            catch(Exception e) {
                e.printStackTrace();
            }


            for(int i=0; i<=4; i++)
            {
                try {
                    if (i == 0)
                        tempSocket = socket0;
                    else if (i == 1)
                        tempSocket = socket1;
                    else if (i == 2)
                        tempSocket = socket2;
                    else if (i == 3)
                        tempSocket = socket3;
                    else if (i == 4)
                        tempSocket = socket4;


                    message = "SEQ:" + " " + String.valueOf(processIdOfResp[i]) + " " + String.valueOf(proposedSeqDouble);
                    dataOutputStream = new DataOutputStream(tempSocket.getOutputStream());
                    dataOutputStream.writeUTF(message);
                    dataOutputStream.flush();

                    DataInputStream dataInputStream = new DataInputStream(tempSocket.getInputStream());
                    String ack = dataInputStream.readUTF();

                    if (ack.equals("ACK")) {
                    }
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch(SocketTimeoutException e)
                {
                    e.printStackTrace();
                }
                catch(StreamCorruptedException e)
                {
                    e.printStackTrace();
                }
                catch(EOFException e)
                {
                    e.printStackTrace();
                }
                catch(FileNotFoundException e)
                {
                    e.printStackTrace();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ClientTask socket IOException");
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }

            }
            try {
                socket0.close();
                socket1.close();
                socket2.close();
                socket3.close();
                socket4.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }
}


