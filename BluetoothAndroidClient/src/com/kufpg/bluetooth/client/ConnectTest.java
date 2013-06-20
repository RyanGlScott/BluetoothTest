package com.kufpg.bluetooth.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ConnectTest extends Activity {
	private static final int REQUEST_ENABLE_BT = 1;
	private BluetoothAdapter mBtAdapter;
	private BluetoothSocket mBtSocket;
	private OutputStream mOutStream;
	private TextView mOutputTextView;

	// Well known SPP UUID
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// SPP server's MAC address. MUST USE ALL CAPS (BECAUSE MAC ADDRESSES MUST BE SHOUTED)
	private static final String MAC_ADDRESS = "EC:55:F9:F6:55:8E";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mOutputTextView = (TextView) findViewById(R.id.textview_output);
		updateText("\n...In onCreate()...");

		Button startServer = (Button) findViewById(R.id.button_start_server);
		startServer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Thread thread = new Thread() {
					@Override
					public void run() {
						mBtAdapter = BluetoothAdapter.getDefaultAdapter();		
						if (!thisThingIsOn()) {
							Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
							startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
						} else {
							updateText("\n...In onResume...\n...Attempting client connect...");

							// Set up a pointer to the remote node using it's address.
							BluetoothDevice device = mBtAdapter.getRemoteDevice(MAC_ADDRESS);

							// Two things are needed to make a connection:
							// A MAC address, which we got above.
							// A Service ID or UUID. In this case we are using the UUID for SPP.
							try {
								mBtSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
							} catch (IOException e) {
								alertBox("Fatal Error", "Socket creation failed: " + e.getMessage() + ".");
							}

							// Discovery is resource intensive. Make sure it isn't going on
							// when you attempt to connect and pass your message.
							mBtAdapter.cancelDiscovery();

							// Establish the connection. This will block until it connects.
							try {
								mBtSocket.connect();
								updateText("\n...Connection established and data link opened...");
							} catch (IOException e) {
								try {
									mBtSocket.close();
								} catch (IOException e2) {
									alertBox("Fatal Error", "Unable to close socket during connection failure" + e2.getMessage() + ".");
								}
							}

							// Create a data stream so we can talk to server.
							updateText("\n...Sending message to server...");
							String message = "Hello from Android.\n";
							updateText("\n\n...The message that we will send to the server is: " + message);

							try {
								mOutStream = mBtSocket.getOutputStream();
							} catch (IOException e) {
								alertBox("Fatal Error", "Output stream creation failed:" + e.getMessage() + ".");
							}

							byte[] msgBuffer = message.getBytes();
							try {
								mOutStream.write(msgBuffer);
							} catch (IOException e) {
								String msg = "Exception occurred during write: " + e.getMessage();
								if (MAC_ADDRESS.equals("00:00:00:00:00:00")) {
									msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 37 in the java code";
								}
								msg = msg + ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";

								alertBox("Fatal Error", msg);
							}


							InputStream inStream;
							try {
								inStream = mBtSocket.getInputStream();
								BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));
								String lineRead = bReader.readLine();
								updateText("\n..." + lineRead + "\n");
								mOutStream.flush();
								mOutStream.close();
								mBtSocket.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				};
				thread.start();
			}
		});
	}

	private boolean thisThingIsOn() {
		// Check for Bluetooth support and then check to make sure it is turned on
		// Emulator doesn't support Bluetooth and will return null
		if (mBtAdapter == null) {
			alertBox("Fatal Error", "Bluetooth Not supported. Aborting.");
		} else {
			if (mBtAdapter.isEnabled()) {
				updateText("\n...Bluetooth is enabled...");
				return true;
			}
		}
		return false;
	}

	public void alertBox(final String title, final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(ConnectTest.this).setTitle(title)
				.setMessage(message + " Press OK to exit.")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).show();
			}
		});
	}

	private void updateText(final String newText) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mOutputTextView.append(newText);
			}
		});
	}
}