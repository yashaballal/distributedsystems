package edu.buffalo.cse.cse486586.simpledht;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    TelephonyManager tel = null;

    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";

    static int count = 0;

    List<String> insertMaintain = new ArrayList<String>();

    String portStr = null;
    String myPort = null;

    static String predSuccStore[] = new String[2];

    List<String> nodeList = new ArrayList<String>();

    static final int SERVER_PORT = 10000;
    private static int seqNum=0;

    private Uri mUri = null;

    public Uri buildUri(String scheme, String authority) {

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.v("Delete", "The selection is: "+selection);
        if(selection.equals("@") || selection.equals("*"))
        {
            for(String keyField: insertMaintain)
            {
                try {
                    File file = new File(getContext().getFilesDir(), genHash(keyField));
                    file.delete();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }

            if(selection.equals("*"))
            {
                Log.v("Delete","Made a new client task");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "DELETE",selection);
            }
        }
        else
        {
            Log.v("Delete", "Deleting individual messages");
            if(!insertMaintain.contains(selection))
            {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "DELETE",selection);
            }
            else
            {
                try {
                    File file = new File(getContext().getFilesDir(), genHash(selection));
                    file.delete();
                    insertMaintain.remove(selection);
                    Log.v("Delete", "Removed the file with name: "+selection+"from this machine");
                }
                catch(NoSuchAlgorithmException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    public void callClient(ContentValues values)
    {
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "INSERT",values.getAsString(KEY_FIELD),values.getAsString(VALUE_FIELD));
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        FileOutputStream outputStream;
        try
        {

            Log.v("Insert", predSuccStore[0]+" "+predSuccStore[1]);
            if( (predSuccStore[0]==null || predSuccStore[1]==null)||
                    (genHash(predSuccStore[0]).compareTo(genHash(portStr))>0 &&
                            (genHash(values.getAsString(KEY_FIELD)).compareTo(genHash(portStr))<=0
                        || genHash(values.getAsString(KEY_FIELD)).compareTo(genHash(predSuccStore[0]))>0)) ||
                    (genHash(predSuccStore[0]).compareTo(genHash(values.getAsString(KEY_FIELD))) < 0 &&
                            genHash(values.getAsString(KEY_FIELD)).compareTo(genHash(portStr)) <= 0 ))
            {
                Log.v("Insert","Inserted in this node: "+count);
                outputStream = getContext().openFileOutput(genHash(values.getAsString(KEY_FIELD)), Context.MODE_PRIVATE);
                insertMaintain.add(values.getAsString(KEY_FIELD));
                String str1 = values.getAsString(VALUE_FIELD);
                System.out.println("Inside insert: "+str1);
                outputStream.write(str1.getBytes());
                //outputStream.flush();
                outputStream.close();
                count++;
                Log.v("insert", values.toString());
            }
            else
            {
                Log.v("Insert", "Passing on the message to the next node");
                callClient(values);
            }
        }
        catch(NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }


    @Override
    public boolean onCreate() {
        System.out.println("Entered onCreate");
        tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try
        {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        if(!myPort.equals(REMOTE_PORT0))
        {
            String message = "CREATE:"+portStr;
            Log.v("message:", message);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);
        }
        else
        {
            addNodeValue(portStr);
        }
        return true;
    }

    public String addNodeValue(String portID)
    {
        String returnValue = null;
        if(nodeList.isEmpty()&&portID.equals("5554"))
        {
            nodeList.add(portID);
            predSuccStore[0]=null;
            predSuccStore[1]=null;
        }
        else
        {

            int index = -1;
            for(String tempValue:nodeList)
            {
                index++;
                try {
                    if(genHash(portID).compareTo(genHash(tempValue)) < 0)
                    {
                        nodeList.add(index,portID);
                        if(index==nodeList.size()-1)
                        {
                            returnValue= nodeList.get(index-1)+":"+nodeList.get(0);
                            break;
                        }
                        else if( index==0 )
                        {
                            returnValue = nodeList.get(nodeList.size()-1)+":"+nodeList.get(index+1);
                            break;
                        }
                        else
                        {
                            returnValue = nodeList.get(index-1)+":"+nodeList.get(index+1);
                            break;
                        }
                    }
                    else if(index==nodeList.size()-1)
                    {
                        nodeList.add(portID);
                        returnValue =  nodeList.get(index)+":"+nodeList.get(0);
                        break;
                    }
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }

        }

        return returnValue;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        FileInputStream fileInputStream = null;
        /*Reference: https://stackoverflow.com/questions/14169661/read-complete-file-without-using-loop-in-java*/
        MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});

        try
        {
            if( !(selection.substring(0,1).equals("*") || selection.equals("@")) && ((predSuccStore[0]==null || predSuccStore[1]==null)||
                    (genHash(predSuccStore[0]).compareTo(genHash(portStr))>0 &&
                            (genHash(selection).compareTo(genHash(portStr))<=0
                                    || genHash(selection).compareTo(genHash(predSuccStore[0]))>0)) ||
                    (genHash(selection).compareTo(genHash(predSuccStore[0])) > 0 &&
                            genHash(selection).compareTo(genHash(portStr)) <= 0 )))
            {
                try
                {
                    fileInputStream = getContext().openFileInput(genHash(selection));

                    //System.out.println("The available data is: "+ fileInputStream.available());
                    /*Reference: http://java-demos.blogspot.com/2012/10/read-a-file-in-java.html*/
                    byte[] data1 = new byte[fileInputStream.available()];
                    fileInputStream.read(data1);
                    matrixCursor.addRow(new Object[]{selection, new String(data1)});
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

            }
            else if(selection.equals("@"))
            {
                for(String insertValue: insertMaintain)
                {
                    try {
                        fileInputStream = getContext().openFileInput(genHash(insertValue));
                        byte[] data1 = new byte[fileInputStream.available()];
                        fileInputStream.read(data1);
                        //System.out.println("Inside query function: "+new String(data1));
                        matrixCursor.addRow(new Object[]{insertValue, new String(data1)});

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if(selection.substring(0,1).equals("*"))
            {
                String store[] = selection.split(":");

                for(String insertValue: insertMaintain)
                {
                    try {
                        fileInputStream = getContext().openFileInput(genHash(insertValue));
                        byte[] data1 = new byte[fileInputStream.available()];
                        fileInputStream.read(data1);
                        //System.out.println("Inside query function: "+new String(data1));
                        matrixCursor.addRow(new Object[]{insertValue, new String(data1)});

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                }

                if((store.length>1 && !(store[1].equals(predSuccStore[1]))) || (store.length == 1
                                                && predSuccStore[0]!=null && predSuccStore[1]!=null) )
                {
                    String response = ClientTaskSynchronous(selection);
                    String temp[] = response.split(":");
                    for(int i=0; i<temp.length-1; i+=2)
                    {
                        matrixCursor.addRow(new Object[]{temp[i+1], temp[i]});
                    }

                }
            }
            else
            {
                String response = ClientTaskSynchronous(selection);
                Log.v("query response", response);
                matrixCursor.addRow(new Object[]{selection, response.split(":")[0]});
            }

            Log.v("query", selection);
            return matrixCursor ;


        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
            ServerSocket serverSocket = sockets[0];
            Socket aSocket = null;

            while (true)
            {
                try {
                aSocket = serverSocket.accept();
                /*Usage of DataInputStream
                Reference:
                https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                DataInputStream dataInputStream = new DataInputStream(aSocket.getInputStream());
                String message = dataInputStream.readUTF();
                Log.v("ServerMsg",message);
                if(message.substring(0,6).equals("CREATE"))
                {
                    String temp[]=message.split(":");
                    String predSucc=addNodeValue(temp[1]);
                    DataOutputStream dataOutputStream= new DataOutputStream(aSocket.getOutputStream());
                    dataOutputStream.writeUTF(predSucc);
                    dataOutputStream.flush();
                    dataOutputStream.close();
                }
                else if(message.substring(0,11).equals("PREDECESSOR"))
                {
                    String temp[]=message.split(":");
                    System.out.println("PREDECESSOR insert for:"+temp[1]);
                    predSuccStore[0]=temp[1];
                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                    /*Sending ACK to client indicating that the message has been published in server
                     * machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                    dataOutputStream.writeUTF("ACK");
                    dataOutputStream.flush();
                    dataOutputStream.close();

                }
                else if(message.substring(0,9).equals("SUCCESSOR"))
                {
                    String temp[]=message.split(":");
                    predSuccStore[1]=temp[1];
                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                    /*Sending ACK to client indicating that the message has been published in server
                     * machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                    dataOutputStream.writeUTF("ACK");
                    dataOutputStream.flush();
                    dataOutputStream.close();
                }
                else if(message.substring(0,6).equals("INSERT"))
                {
                    /*Usage of DataOutputStream
                    Reference:
                    https://www.geeksforgeeks.org/dataoutputstream-in-java/
                    https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                    String temp[]=message.split(":");
                    ContentValues cv = new ContentValues();
                    cv.put(KEY_FIELD, temp[1]);
                    cv.put(VALUE_FIELD, temp[2]);

                    insert(mUri,cv);
                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                    /*Sending ACK to client indicating that the message has been published in server
                     * machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                    dataOutputStream.writeUTF("ACK");
                    dataOutputStream.flush();
                    dataOutputStream.close();
                }
                else if(message.substring(0,5).equals("QUERY"))
                {
                    String temp[]= message.split(":");

                    MatrixCursor matrixCursor = null;
                    if(!temp[1].equals("*"))
                    {
                        matrixCursor = (MatrixCursor) query(mUri, null, temp[1],null,null,null);
                    }
                    else
                    {
                        matrixCursor = (MatrixCursor) query(mUri, null, temp[1]+":"+temp[2],null,null,null);
                    }

                    String keyData , valueData = null;

                    StringBuffer queryAck = new StringBuffer("");
                    while(matrixCursor.moveToNext()) {

                        int valueIndex = matrixCursor.getColumnIndex(VALUE_FIELD);
                        int keyIndex = matrixCursor.getColumnIndex(KEY_FIELD);
                        valueData = matrixCursor.getString(valueIndex);
                        keyData = matrixCursor.getString(keyIndex);
                        Log.v("ServerTask", valueData+" "+keyData);
                        queryAck.append(valueData+":"+keyData+":");

                    }

                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                    /*Sending ACK to client indicating that the message has been published in server
                     * machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                    dataOutputStream.writeUTF(queryAck.toString());
                    dataOutputStream.flush();
                    dataOutputStream.close();
                    matrixCursor.close();

                }
                else if(message.substring(0,6).equals("DELETE"))
                {
                    /*Usage of DataOutputStream
                    Reference:
                    https://www.geeksforgeeks.org/dataoutputstream-in-java/
                    https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                    String temp[]=message.split(":");

                    delete(mUri,temp[1], null);
                    DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

                    /*Sending ACK to client indicating that the message has been published in server
                     * machine
                     * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
                    dataOutputStream.writeUTF("ACK");
                    dataOutputStream.flush();
                    dataOutputStream.close();
                }

                dataInputStream.close();

                System.out.println("The LOCAL predecessor successor list at this point is:"+predSuccStore[0]+" "+predSuccStore[1]);
                }
                catch(EOFException e)
                {
                    e.printStackTrace();
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            Cursor resultCursor = query(mUri, null, strReceived, null, null);
            int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
            resultCursor.moveToFirst();

            String returnValue = resultCursor.getString(valueIndex);
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */


            /*
            * Implementation of displaying on the avd and storing in the file was here.
            * */
            return;
        }
    }

    public String ClientTaskSynchronous( String... msgs)
    {

        Socket socketsucc = null;
        String selection = msgs[0];
        String msgToSend = null;

        if(selection.equals("*")) {
            msgToSend = "QUERY:"+selection+":"+portStr;
        }
        else {
            msgToSend = "QUERY:" + selection;
        }
        DataOutputStream dataOutputStream = null;

        /*
         **Usage of DataOutputStream
         **Reference:
         **https://www.geeksforgeeks.org/dataoutputstream-in-java/
         **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
        try
        {
            Log.v("ClientTask", "Sending the message to the avd:"+predSuccStore[1]);
            Log.v("ClientTask", "Message being sent:"+msgToSend);
            socketsucc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(predSuccStore[1])*2);

            dataOutputStream = new DataOutputStream(socketsucc.getOutputStream());
            dataOutputStream.writeUTF(msgToSend);
            dataOutputStream.flush();

            /* Usage of DataInputStream
             * Reference:
             * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
             * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
             * */
            DataInputStream dataInputStream = new DataInputStream(socketsucc.getInputStream());
            String response = dataInputStream.readUTF();

            dataInputStream.close();
            dataOutputStream.close();
            socketsucc.close();

            return response;
        }
        catch (UnknownHostException e) {
               e.printStackTrace();
        } catch(EOFException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Socket socket0 = null;
            Socket socketpre = null;
            Socket socketsucc = null;

            DataOutputStream dataOutputStream = null;
            try {

                String message = msgs[0];
                if(message.substring(0,6).equals("CREATE"))
                {
                    socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT0));
                    /*
                     **Usage of DataOutputStream
                     **Reference:
                     **https://www.geeksforgeeks.org/dataoutputstream-in-java/
                     **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                    dataOutputStream = new DataOutputStream(socket0.getOutputStream());
                    dataOutputStream.writeUTF(message);
                    dataOutputStream.flush();

                    /* Usage of DataInputStream
                     * Reference:
                     * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                     * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
                     * */
                    DataInputStream dataInputStream = new DataInputStream(socket0.getInputStream());
                    String ack = dataInputStream.readUTF();
                    predSuccStore=ack.split(":");
                    dataInputStream.close();
                    dataOutputStream.close();
                    socket0.close();


                    socketpre = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(predSuccStore[0])*2);

                    dataOutputStream = new DataOutputStream(socketpre.getOutputStream());
                    /*Declaring that current node is the successor of the previous node*/
                    dataOutputStream.writeUTF("SUCCESSOR:"+portStr);
                    dataOutputStream.flush();

                    dataInputStream = new DataInputStream(socketpre.getInputStream());
                    ack = dataInputStream.readUTF();
                    if(ack.equals("ACK"))
                    {
                        System.out.println("Received ACK from predecessor");
                    }
                    dataInputStream.close();
                    dataOutputStream.close();
                    socketpre.close();

                    socketsucc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(predSuccStore[1])*2);

                    dataOutputStream = new DataOutputStream(socketsucc.getOutputStream());
                    /*Declaring that the current node is the predecessor of the next node*/
                    dataOutputStream.writeUTF("PREDECESSOR:"+portStr);
                    dataOutputStream.flush();

                    dataInputStream = new DataInputStream(socketsucc.getInputStream());
                    ack = dataInputStream.readUTF();
                    if(ack.equals("ACK"))
                    {
                        System.out.println("Received ACK from successor");
                    }
                    dataInputStream.close();
                    dataOutputStream.close();
                    socketsucc.close();

                }
                else if(message.equals("INSERT"))
                {
                    String msgToSend = "INSERT:"+msgs[1]+":"+msgs[2];
                    /*
                     **Usage of DataOutputStream
                     **Reference:
                     **https://www.geeksforgeeks.org/dataoutputstream-in-java/
                     **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
                    Log.v("ClientTask", "Sending the message to the avd:"+predSuccStore[1]);
                    Log.v("ClientTask", "Message being sent:"+msgToSend);
                    socketsucc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(predSuccStore[1])*2);

                    dataOutputStream = new DataOutputStream(socketsucc.getOutputStream());
                    dataOutputStream.writeUTF(msgToSend);
                    dataOutputStream.flush();

                    /* Usage of DataInputStream
                     * Reference:
                     * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                     * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
                     * */
                    DataInputStream dataInputStream = new DataInputStream(socketsucc.getInputStream());
                    String ack = dataInputStream.readUTF();

                    if (ack.equals("ACK")) {
                        System.out.println("GOT IT");
                    }
                    dataInputStream.close();
                    dataOutputStream.close();
                    socketsucc.close();
                }
                else if(message.equals("DELETE")) {
                    String msgToSend = "DELETE:"+msgs[1];
                    socketsucc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(predSuccStore[1]) * 2);

                    dataOutputStream = new DataOutputStream(socketsucc.getOutputStream());
                    dataOutputStream.writeUTF(msgToSend);
                    dataOutputStream.flush();

                    /* Usage of DataInputStream
                     * Reference:
                     * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
                     * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
                     * */
                    DataInputStream dataInputStream = new DataInputStream(socketsucc.getInputStream());
                    String ack = dataInputStream.readUTF();

                    if (ack.equals("ACK")) {
                        System.out.println("GOT IT");

                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch(EOFException e)
            {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }

}
