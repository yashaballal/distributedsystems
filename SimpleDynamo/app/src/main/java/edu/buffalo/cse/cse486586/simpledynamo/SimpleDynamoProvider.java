package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {

	static final String TAG = SimpleDynamoProvider.class.getSimpleName();

	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	static final int SERVER_PORT = 10000;
	boolean dataRetrieved = false;
	ArrayList<String> hashNodes = new ArrayList<String>();
	ArrayList<MsgBelongs> maintainInserted = new ArrayList<MsgBelongs>();


	private Uri mUri = null;
	TelephonyManager tel = null;
	String portStr = null;
	String myPort = null;


	public Uri buildUri(String scheme, String authority) {

		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}


	public class MsgBelongs
	{
		String key;
		String belongsTo;

		MsgBelongs(String key, String belongsTo)
		{
			this.key = key;
			this.belongsTo = belongsTo;
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		String getFrom = null;
		if(selection.equals("*") || selection.equals("@"))
		{
//			for(MsgBelongs insertValue: maintainInserted)
//			{
//				try {
//					File file = new File(getContext().getFilesDir(), genHash(insertValue.key));
//					file.delete();
//				} catch (NoSuchAlgorithmException e) {
//					e.printStackTrace();
//				}
//			}

			for( String fileName :this.getContext().fileList())
			{
				File file = new File(getContext().getFilesDir(), fileName);
				file.delete();
			}

			if(selection.equals("*"))
			{
				ClientTaskDeleteSynchronous(selection);
			}

		}
		else
		{
			for (int i = 0; i < hashNodes.size(); i++)
			{
				try {
					if ((genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(selection)) < 0
							&& genHash(hashNodes.get(i)).compareTo(genHash(selection)) >= 0) ||
							((genHash(hashNodes.get((i + hashNodes.size() -1)%hashNodes.size())).compareTo(genHash(selection)) < 0 ||
									genHash(hashNodes.get(i % hashNodes.size())).compareTo(genHash(selection)) >= 0) &&
									genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(hashNodes.get(i))) > 0 ))
					{
						Log.v("Delete", "Reached in the condition");
						getFrom = hashNodes.get(i);
						break;
					}


				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
			if(getFrom.equals(portStr))
			{
				File file = new File(getContext().getFilesDir(), selection);
				file.delete();
			}
			else
			{
				try {
					File file = new File(getContext().getFilesDir(), selection);
					file.delete();
				}catch (NullPointerException e)
				{
					e.printStackTrace();
				}
				ClientTaskDeleteSynchronous(selection, getFrom);
			}
		}
		return 0;
	}

	public int deleteInternal(Uri uri, String selection, String[] selectionArgs)
	{
		String temp[] = selection.split(":");
		if(temp[0].equals("*"))
		{
			for(MsgBelongs insertValue: maintainInserted)
			{
				File file = new File(getContext().getFilesDir(), insertValue.key);
				file.delete();
			}

			Log.v("deleteInternal", "Reached this node for * query initiated by:"+temp[1]);
			if(!hashNodes.get((hashNodes.indexOf(portStr) + 1)%hashNodes.size()).equals(temp[1]))
			{
				ClientTaskDeleteSynchronous(selection, hashNodes.get((hashNodes.indexOf(portStr)+1)%hashNodes.size()));
			}
		}
		else
		{
			File file = new File(getContext().getFilesDir(), selection);
			file.delete();
		}
		return 0;

	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}


	public void callClient(ContentValues values, String successorNode)
	{
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "INSERT",values.getAsString(KEY_FIELD),values.getAsString(VALUE_FIELD), successorNode);
	}

	public Uri insertInternal(Uri uri, ContentValues values) {

		while(dataRetrieved == false)
		{
			Log.v("INSERTI", "Waiting for dataRetrieved flag to become true");
		}

		try{
			FileOutputStream outputStream;
			outputStream = getContext().openFileOutput(values.getAsString(KEY_FIELD), Context.MODE_PRIVATE);
			String str1 = values.getAsString(VALUE_FIELD);
			outputStream.write(str1.getBytes());
			outputStream.close();
			Log.v("INSERTI", values.toString());
		}
		catch (IOException e)
		{
				e.printStackTrace();
		}

		return mUri;
	}

	public Uri insertRecovery(Uri uri, ContentValues values)
	{
		try{
			FileOutputStream outputStream;
			outputStream = getContext().openFileOutput(values.getAsString(KEY_FIELD), Context.MODE_PRIVATE);
			String str1 = values.getAsString(VALUE_FIELD);
			outputStream.write(str1.getBytes());
			outputStream.close();
			Log.v("INSERTR", values.toString());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return mUri;
	}


	@Override
	public Uri insert(Uri uri, ContentValues values)
	{

		FileOutputStream outputStream;
		String insertAt = null;
		try {
				for (int i = 0; i < hashNodes.size(); i++)
				{
					Log.v("INSERT", hashNodes.get((i + hashNodes.size() -1) % hashNodes.size()) + "   "+ String.valueOf(i));
					if ((genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(values.getAsString(KEY_FIELD))) < 0
							&& genHash(hashNodes.get(i)).compareTo(genHash(values.getAsString(KEY_FIELD))) >= 0) ||
							((genHash(hashNodes.get((i + hashNodes.size() -1)%hashNodes.size())).compareTo(genHash(values.getAsString(KEY_FIELD))) < 0 ||
									genHash(hashNodes.get(i)).compareTo(genHash(values.getAsString(KEY_FIELD))) >= 0) &&
										genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(hashNodes.get(i))) > 0 ))
					{
						Log.v("INSERT", "Reached in the condition");
						insertAt = hashNodes.get(i);
						break;
					}
				}
				Log.v("INSERT","Value of insert at is:"+insertAt);

				while(dataRetrieved == false)
				{
					Log.v("INSERT", "Waiting for dataRetrieved to become true:"+values.getAsString(KEY_FIELD));
				}

				if (insertAt.equals(portStr) && dataRetrieved==true)
				{
					outputStream = getContext().openFileOutput(values.getAsString(KEY_FIELD), Context.MODE_PRIVATE);
					String str1 = values.getAsString(VALUE_FIELD);
					outputStream.write(str1.getBytes());
					outputStream.close();
					Log.v("INSERT", values.toString());
					maintainInserted.add(new MsgBelongs(values.getAsString(KEY_FIELD),portStr));
				}
				else
				{
					Log.v("INSERT", "Passing on the message to the next node");
					Log.v("INSERT", values.toString());
				}
				callClient(values, insertAt);

			}
		 catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return mUri;
	}


	@Override
	public boolean onCreate() {
		tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		for( String fileName :this.getContext().fileList())
		{
			File file = new File(getContext().getFilesDir(), fileName);
			file.delete();
		}

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


		boolean inserted = false;

		Log.v("onCreate", myPort);

		/*Insert hashvalues into arraylist in order*/
		for (int i = 5554; i <= 5562; i += 2)
		{
			inserted = false;
			int count = 0;
			Iterator<String> iterator = hashNodes.iterator();
			while (iterator.hasNext()) {
				try {
					if (genHash(iterator.next()).compareTo(genHash(String.valueOf(i))) > 0) {
						inserted = true;
						hashNodes.add(count, String.valueOf(i));
						break;
					}
					count++;
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
			if(inserted == false)
			{
				hashNodes.add(String.valueOf(i));
			}
		}

		Iterator<String> iterator = hashNodes.iterator();
		while(iterator.hasNext())
		{
			Log.v("onCreate",iterator.next());
		}

		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"RETRIEVE", portStr, hashNodes.get((hashNodes.indexOf(portStr)+1)%hashNodes.size()));
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"RETRIEVE", hashNodes.get((hashNodes.indexOf(portStr) + hashNodes.size() - 1)%hashNodes.size()), hashNodes.get((hashNodes.indexOf(portStr) + hashNodes.size()-1)%hashNodes.size()));
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"RETRIEVE", hashNodes.get((hashNodes.indexOf(portStr) + hashNodes.size() - 2)%hashNodes.size()), hashNodes.get((hashNodes.indexOf(portStr) + hashNodes.size()-2)%hashNodes.size()));
		return true;
	}



	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		String getFrom = null;
		FileInputStream fileInputStream = null;
		/*Reference: https://stackoverflow.com/questions/14169661/read-complete-file-without-using-loop-in-java*/
		MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});

		try{
			if(!(selection.equals("*") || selection.equals("@")))
			{
				for (int i = 0; i < hashNodes.size(); i++)
				{
					if ((genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(selection)) < 0
							&& genHash(hashNodes.get(i % hashNodes.size())).compareTo(genHash(selection)) >= 0) ||
							((genHash(hashNodes.get((i + hashNodes.size() -1)%hashNodes.size())).compareTo(genHash(selection)) < 0 ||
									genHash(hashNodes.get(i % hashNodes.size())).compareTo(genHash(selection)) >= 0) &&
									genHash(hashNodes.get((i + hashNodes.size() -1) % hashNodes.size())).compareTo(genHash(hashNodes.get(i))) > 0 ))
					{
						Log.v("QUERY", "Reached in the condition");
						getFrom = hashNodes.get(i);
						break;
					}
				}

			}

			if( selection.equals("*") || selection.equals("@") || getFrom.equals(portStr))
			{
				if(selection.equals("@") || selection.equals("*"))
				{


//					for(MsgBelongs insertValue: maintainInserted)
//				    {
//						Log.v("QUERY", insertValue.key);
//						try {
//							fileInputStream = getContext().openFileInput(genHash(insertValue.key));
//							byte[] dataFile = new byte[fileInputStream.available()];
//							fileInputStream.read(dataFile);
//							//System.out.println("Inside query function: "+new String(data1));
//							matrixCursor.addRow(new Object[]{insertValue.key, new String(dataFile)});
//
//						} catch (FileNotFoundException e) {
//							e.printStackTrace();
//						}
//						catch (IOException e) {
//							e.printStackTrace();
//						}
//						catch (NoSuchAlgorithmException e) {
//							e.printStackTrace();
//						}
//
//
//					}

					for(String fileName :this.getContext().fileList())
					{
						Log.v("QUERY", fileName);
						try {
							fileInputStream = getContext().openFileInput(fileName);
							byte[] dataFile = new byte[fileInputStream.available()];
							fileInputStream.read(dataFile);
							//System.out.println("Inside query function: "+new String(data1));
							matrixCursor.addRow(new Object[]{fileName, new String(dataFile)});

						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
						catch (IOException e) {
							e.printStackTrace();
						}

					}
					if(selection.equals("*"))
					{
						for(int j=0; j<hashNodes.size(); j++)
						{
						    if(!portStr.equals(hashNodes.get(j)))
                            {
                                String response = ClientTaskSynchronous(selection, hashNodes.get(j));
                                String temp[] = response.split(":");
                                if(temp.length>=2){
									for(int i=0; i<temp.length-1; i+=2)
									{
										Log.v("QUERY", "Key is:"+temp[i+1]);
										matrixCursor.addRow(new Object[]{temp[i+1], temp[i]});
									}
								}

                            }

						}
					}

				}
				else
				{
					fileInputStream = getContext().openFileInput(selection);
					//System.out.println("The available data is: "+ fileInputStream.available());
					/*Reference: http://java-demos.blogspot.com/2012/10/read-a-file-in-java.html*/
					byte[] data1 = new byte[fileInputStream.available()];
					fileInputStream.read(data1);
					matrixCursor.addRow(new Object[]{selection, new String(data1)});
				}
			}
			else
			{
				int i =0;
				String response = null;
				while(response == null || response.length()==0)
				{
					Log.v("QUERY", "The avd has currently failed so requesting from the next one");
					response = ClientTaskSynchronous(selection, hashNodes.get((hashNodes.indexOf(getFrom)+i)%hashNodes.size()));

					//Log.v("QUERY", response);
					i=(i+1)%3;
				}
				Log.v("QUERY","The partition is: "+getFrom+"The key is:"+selection+"The value is:"+response.split(":")[0]);
				matrixCursor.addRow(new Object[]{selection, response.split(":")[0]});
			}
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return matrixCursor;
	}

	public Cursor queryInternal(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {

		String getFrom = null;
		FileInputStream fileInputStream = null;
		/*Reference: https://stackoverflow.com/questions/14169661/read-complete-file-without-using-loop-in-java*/
		MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});

		if(selection.equals("*"))
		{
			for(MsgBelongs insertValue: maintainInserted)
			{
				try {
					fileInputStream = getContext().openFileInput(insertValue.key);
					byte[] dataFile = new byte[fileInputStream.available()];
					fileInputStream.read(dataFile);
					//System.out.println("Inside query function: "+new String(data1));
					matrixCursor.addRow(new Object[]{insertValue.key, new String(dataFile)});

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			try{
				fileInputStream = getContext().openFileInput(selection);
				//System.out.println("The available data is: "+ fileInputStream.available());
				/*Reference: http://java-demos.blogspot.com/2012/10/read-a-file-in-java.html*/
				byte[] data1 = new byte[fileInputStream.available()];
				fileInputStream.read(data1);
				matrixCursor.addRow(new Object[]{selection, new String(data1)});
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}

		}

		return matrixCursor;
	}


	Cursor retrieveMessages(String portStrOther)
	{
		FileInputStream fileInputStream = null;
		/*Reference: https://stackoverflow.com/questions/14169661/read-complete-file-without-using-loop-in-java*/
		MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});

		while (!dataRetrieved){
			continue;
		}
		for(MsgBelongs insertValue: maintainInserted)
		{
			if((insertValue.belongsTo.equals(portStrOther)))
			{
				try {
					fileInputStream = getContext().openFileInput(insertValue.key);
					byte[] dataFile = new byte[fileInputStream.available()];
					fileInputStream.read(dataFile);
					//System.out.println("Inside query function: "+new String(data1));
					matrixCursor.addRow(new Object[]{insertValue.key, new String(dataFile)});

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return matrixCursor;

	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
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
			mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");
			ServerSocket serverSocket = sockets[0];
			Socket aSocket = null;

			while (true)
			{
				try {
					Log.v("ServerTask", "Waiting for connection");
					aSocket = serverSocket.accept();
					aSocket.setSoTimeout(1000);
					/*Usage of DataInputStream
					Reference:
					https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
					https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
					DataInputStream dataInputStream = new DataInputStream(aSocket.getInputStream());
					String message = dataInputStream.readUTF();
					Log.v("ServerTask",message);

					if(message.substring(0,6).equals("INSERT"))
					{
						Log.v("ServerTask", "Got an insert message");
						/*Usage of DataOutputStream
						Reference:
						https://www.geeksforgeeks.org/dataoutputstream-in-java/
						https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
						String temp[]=message.split(":");
						ContentValues cv = new ContentValues();
						cv.put(KEY_FIELD, temp[1]);
						cv.put(VALUE_FIELD, temp[2]);
						insertInternal(mUri,cv);
						maintainInserted.add(new MsgBelongs(temp[1],temp[3]));
						DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

						/*Sending ACK to client indicating that the message has been published in server
						 * machine
						 * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
						dataOutputStream.writeUTF("ACK");
						Log.v("ServerTask", "Sent the ACK");
						dataOutputStream.flush();
						dataOutputStream.close();
					}
					else if(message.substring(0,5).equals("QUERY"))
					{
						String temp[]= message.split(":");

						MatrixCursor matrixCursor = null;
						matrixCursor = (MatrixCursor) queryInternal(mUri, null, temp[1],null,null);

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
						if(!temp[1].equals("*"))
						{
							deleteInternal(mUri, temp[1], null);
							DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());
							/*Sending ACK to client indicating that the message has been published in server
							 * machine
							 * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
							dataOutputStream.writeUTF("ACK");
							dataOutputStream.flush();
							dataOutputStream.close();

						}
						else
						{
							deleteInternal(mUri,temp[1]+":"+temp[2], null);
							DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());
							/*Sending ACK to client indicating that the message has been published in server
							 * machine
							 * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
							dataOutputStream.writeUTF("ACK");
							dataOutputStream.flush();
							dataOutputStream.close();
						}

					}
					else if(message.substring(0,8).equals("RETRIEVE"))
					{
						/*Usage of DataOutputStream
						Reference:
						https://www.geeksforgeeks.org/dataoutputstream-in-java/
						https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
						String temp[] = message.split(":");
						MatrixCursor matrixCursor = (MatrixCursor) retrieveMessages(temp[1]);
						String keyData , valueData = null;

						StringBuffer retrieveAck = new StringBuffer("");
						while(matrixCursor.moveToNext()) {

							int valueIndex = matrixCursor.getColumnIndex(VALUE_FIELD);
							int keyIndex = matrixCursor.getColumnIndex(KEY_FIELD);
							valueData = matrixCursor.getString(valueIndex);
							keyData = matrixCursor.getString(keyIndex);
							Log.v("ServerTask", valueData+" "+keyData);
							retrieveAck.append(valueData+":"+keyData+":");

						}

						DataOutputStream dataOutputStream = new DataOutputStream(aSocket.getOutputStream());

						/*Sending ACK to client indicating that the message has been published in server
						 * machine
						 * Reference: https://codereview.stackexchange.com/questions/149905/sending-ack-nack-for-packets*/
						dataOutputStream.writeUTF(retrieveAck.toString());
						dataOutputStream.flush();
						dataOutputStream.close();
						matrixCursor.close();

					}

					dataInputStream.close();

				}
				catch (UnknownHostException e) {
					e.printStackTrace();
				}
				catch(SocketTimeoutException e)
				{
					e.printStackTrace();
				}
				catch(StreamCorruptedException e)
				{
					e.printStackTrace();
				}
				catch(FileNotFoundException e)
				{
					e.printStackTrace();
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



	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			Socket socketSendMain = null;
			String message = null;

			DataOutputStream dataOutputStream = null;
			message = msgs[0];
			if(message.equals("INSERT"))
			{
				Log.v("ClientTask:","Reached the insert condition");
				String msgToSend = "INSERT:"+msgs[1]+":"+msgs[2]+":"+msgs[3];

				for(int i=0; i<3; i++)
				{
					try
					{

					String portSend = hashNodes.get(((hashNodes.indexOf(msgs[3]))+i)%(hashNodes.size()));
					Log.v("ClientTask:","Reached the not equals condition");
					socketSendMain = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(portSend)*2);
					socketSendMain.setSoTimeout(1000);
					dataOutputStream = new DataOutputStream(socketSendMain.getOutputStream());
					dataOutputStream.writeUTF(msgToSend);
					dataOutputStream.flush();

					Log.v("ClientTask:","Wrote the message");

					/* Usage of DataInputStream
					 * Reference:
					 * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
					 * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
					 * */
					DataInputStream dataInputStream = new DataInputStream(socketSendMain.getInputStream());
					String ack = dataInputStream.readUTF();

					if (ack.equals("ACK")) {
						Log.v("ClientTask","GOT IT");
					}
					dataInputStream.close();
					dataOutputStream.close();
					socketSendMain.close();
					}catch (UnknownHostException e) {
						e.printStackTrace();
					}
					catch(SocketTimeoutException e)
					{
						e.printStackTrace();
					}
					catch(StreamCorruptedException e)
					{
						e.printStackTrace();
					}
					catch(FileNotFoundException e)
					{
						e.printStackTrace();
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
				else if(message.equals("RETRIEVE"))
				{
					try {
						Log.v("ClientTask:","Reached the insert condition");
					String msgToSend = "RETRIEVE:"+msgs[1];

					socketSendMain = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(msgs[2])*2);
					socketSendMain.setSoTimeout(1000);
					/*
					 **Usage of DataOutputStream
					 **Reference:
					 **https://www.geeksforgeeks.org/dataoutputstream-in-java/
					 **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
					dataOutputStream = new DataOutputStream(socketSendMain.getOutputStream());
					dataOutputStream.writeUTF(msgToSend);
					dataOutputStream.flush();

					Log.v("ClientTask","Wrote the message");

					/* Usage of DataInputStream
					 * Reference:
					 * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
					 * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
					 * */
					DataInputStream dataInputStream = new DataInputStream(socketSendMain.getInputStream());
					String ackRetrieve = dataInputStream.readUTF();
					String retKeyValue[] = ackRetrieve.split(":");

					for(int i=0; i<retKeyValue.length-1; i+=2)
					{
						Log.v("ClientTask", "Key is:"+retKeyValue[i+1]);
						ContentValues cv = new ContentValues();
						cv.put(KEY_FIELD, retKeyValue[i+1]);
						cv.put(VALUE_FIELD, retKeyValue[i]);
						insertRecovery(mUri,cv);

						maintainInserted.add(new MsgBelongs(retKeyValue[i+1], msgs[1]));

					}

					dataInputStream.close();
					dataOutputStream.close();
					socketSendMain.close();
					}catch (UnknownHostException e) {
						e.printStackTrace();
					}
					catch(SocketTimeoutException e)
					{
						e.printStackTrace();
					}
					catch(StreamCorruptedException e)
					{
						e.printStackTrace();
					}
					catch(FileNotFoundException e)
					{
						e.printStackTrace();
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
			if(message.equals("RETRIEVE")){
				if(msgs[1].equals(hashNodes.get((hashNodes.indexOf(portStr)+hashNodes.size()-2)%hashNodes.size())))
				{
					Log.v("ClientTask", "Set the dataRetrieved as true");
					dataRetrieved = true;
				}
			}
			return null;
		}
	}


	public String ClientTaskSynchronous( String... msgs)
	{

		Socket socketSend = null;
		String selection = msgs[0];
		String msgToSend = "QUERY:" + selection;
		DataOutputStream dataOutputStream = null;
		String response = "";

		/*
		 **Usage of DataOutputStream
		 **Reference:
		 **https://www.geeksforgeeks.org/dataoutputstream-in-java/
		 **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
		try
		{
			socketSend = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(msgs[1])*2);
			socketSend.setSoTimeout(1000);

			dataOutputStream = new DataOutputStream(socketSend.getOutputStream());
			dataOutputStream.writeUTF(msgToSend);
			dataOutputStream.flush();

			/* Usage of DataInputStream
			 * Reference:
			 * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
			 * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
			 * */
			Log.v("ClientTaskSynchronous:", "Sending the query request to the node:"+msgs[1]);

			DataInputStream dataInputStream = new DataInputStream(socketSend.getInputStream());
			response = dataInputStream.readUTF();

			dataInputStream.close();
			dataOutputStream.close();
			socketSend.close();

			return response;
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
		}
		catch(SocketTimeoutException e)
		{
			e.printStackTrace();
		}
		catch(StreamCorruptedException e)
		{
			e.printStackTrace();
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
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

		return response;

	}

	public String ClientTaskDeleteSynchronous( String... msgs)
	{

		Socket socketSend = null;
		String selection = msgs[0];
		String msgToSend = null;

		if(selection.equals("*")) {
			msgToSend = "DELETE:"+selection+":"+portStr;
		}
		else {
			msgToSend = "DELETE:"+selection;
		}
		DataOutputStream dataOutputStream = null;

		/*
		 **Usage of DataOutputStream
		 **Reference:
		 **https://www.geeksforgeeks.org/dataoutputstream-in-java/
		 **https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue*/
		try
		{
			socketSend = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(msgs[1])*2);
			socketSend.setSoTimeout(1000);

			dataOutputStream = new DataOutputStream(socketSend.getOutputStream());
			dataOutputStream.writeUTF(msgToSend);
			dataOutputStream.flush();

			/* Usage of DataInputStream
			 * Reference:
			 * https://www.geeksforgeeks.org/java-io-datainputstream-class-java-set-1/
			 * https://stackoverflow.com/questions/28187038/tcp-client-server-program-datainputstream-dataoutputstream-issue
			 * */
			Log.v("ClientSynchronous:", "Sending the delete request to the node:"+msgs[1]);

			DataInputStream dataInputStream = new DataInputStream(socketSend.getInputStream());
			String response = dataInputStream.readUTF();

			dataInputStream.close();
			dataOutputStream.close();
			socketSend.close();
			return null;
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
		}
		catch(SocketTimeoutException e)
		{
			e.printStackTrace();
		}
		catch(StreamCorruptedException e)
		{
			e.printStackTrace();
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
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
		return null;

	}


}
