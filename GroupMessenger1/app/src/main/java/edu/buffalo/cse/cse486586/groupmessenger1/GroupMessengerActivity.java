package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentProviderClient;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

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

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private static int seqNum=0;

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

        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        System.out.println("The PORT number is"+ myPort);

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            serverSocket.setReuseAddress(true);
            System.out.println("Checkpoint 0");

            System.out.println("Checkpoint 1");
            new ServerTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, serverSocket);
            System.out.println("Checkpoint 2");
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
        //String msg = "";

        final EditText editText = (EditText) findViewById(R.id.editText1);
        /*if(editText.getText()!=null)
            msg = editText.getText().toString() + "\n";
        editText.setText(""); // This is one way to reset the input box.*/


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

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            System.out.println("DO IN BACKGROUND");
            ServerSocket serverSocket = sockets[0];
            Socket aSocket = null;

            try {
                while (true) {
                    aSocket = serverSocket.accept();
                    System.out.println("Accepted socket connection");
                /*Usage of DataInputStream
                Reference:
                https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                DataInputStream dataInputStream = new DataInputStream(aSocket.getInputStream());
                String message = dataInputStream.readUTF();
                System.out.println("Read the dataInputStream");
                System.out.println("The message is: "+message);
                ContentValues cv = new ContentValues();
                cv.put(KEY_FIELD, String.valueOf(seqNum));
                cv.put(VALUE_FIELD, message);
                getContentResolver().insert(mUri,cv);
                System.out.println("Success print");

                System.out.println("RETURN VALUE IS: "+message);
                publishProgress(String.valueOf(seqNum));

                /*Usage of DataOutputStream
                Reference:
                https://www.geeksforgeeks.org/dataoutputstream-in-java/
                https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/

                DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                /*Sending ACK to client indicating that the message has been published in server
                 * machine
                 * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                dataOutputStream.writeUTF("ACK");
                dataOutputStream.flush();
                dataOutputStream.close();
                dataInputStream.close();
                seqNum++;
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
            System.out.println(strReceived);
            Cursor resultCursor = getContentResolver().query(mUri, null, strReceived, null, null);
            int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
            resultCursor.moveToFirst();

            System.out.println("The value index is: "+valueIndex);
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

    private class OnSendClickListener implements View.OnClickListener
    {
        private static final String KEY_FIELD = "key";
        private static final String VALUE_FIELD = "value";

        private final TextView mTextView;
        private final ContentResolver mContentResolver;
        private final Uri mUri;
        private EditText editText;


        public OnSendClickListener(TextView _tv, ContentResolver _cr, EditText editText) {
            System.out.println("Came here");
            mTextView = _tv;
            mContentResolver = _cr;
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
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
            System.out.println("Executed this");
            System.out.println("Reached here");
            String message = editText.getText().toString();
            editText.setText("");
            new ClientTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
            System.out.println("Created new client task");
            //System.out.println("The content values are: "+mContentValues.toString());
            //System.out.println("Ended this");
        }

    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Socket socket0 = null;
            Socket socket1 = null;
            Socket socket2 = null;
            Socket socket3 = null;
            Socket socket4 = null;

            DataOutputStream dataOutputStream = null;
            try {

                System.out.println("Reached till socket creation");
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
                System.out.println("Created the sockets");

                String message = msgs[0];

                String socketName = "socket";

            /*Usage of DataOutputStream
            Reference:
            https://www.geeksforgeeks.org/dataoutputstream-in-java/
            https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
            Socket tempSocket = null;
            for(int i =0; i<=4; i++) {
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

                System.out.println("Inside ClientTask 1");
                dataOutputStream = new DataOutputStream(tempSocket.getOutputStream());
                dataOutputStream.writeUTF(message);
                dataOutputStream.flush();
                System.out.println("Inside ClientTask 2");
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
                System.out.println("Inside ClientTask 3");
                DataInputStream dataInputStream = new DataInputStream(tempSocket.getInputStream());
                String ack = null;
                ack = dataInputStream.readUTF();
                System.out.println("Inside ClientTask 4");
                if (ack.equals("ACK")) {
                    System.out.println("GOT IT");
                }
                dataInputStream.close();
                dataOutputStream.close();
            }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }
            finally {
                /*
                 * Enclosing the socket.close() in a finally so that it is executed even in case of an
                 * exception
                 * */
                try {
                    socket0.close();
                    socket1.close();
                    socket2.close();
                    socket3.close();
                    socket4.close();
                }catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }


}


