package kufpg.bluetooth.client;

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
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class BluetoothClientActivity extends Activity {
	// Well known SPP UUID
	public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// SPP server's MAC address. MUST USE ALL CAPS (BECAUSE MAC ADDRESSES MUST BE SHOUTED)
	public static final String MAC_ADDRESS = "EC:55:F9:F6:55:8E";

	private static final int REQUEST_ENABLE_BT = 8675309;
	private TextView mLogTextView;
	private StickyButton mStartButton;
	private Button mClearTextButton;
	private ScrollView mScrollView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mLogTextView = (TextView) findViewById(R.id.textview_output);
		mScrollView = (ScrollView) findViewById(R.id.scrollview_output);
		mStartButton = (StickyButton) findViewById(R.id.button_start_server);
		mStartButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
				ConnectThread thread = new ConnectThread();
				thread.start();
			}
		});
		mClearTextButton = (Button) findViewById(R.id.button_clear_text);
		mClearTextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mLogTextView.setText("");
			}
		});
		
		if (savedInstanceState != null) {
			mLogTextView.setText(savedInstanceState.getString("log"));
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == RESULT_OK) {
				appendMessage("Bluetooth enabled! Restarting...");
				ConnectThread thread = new ConnectThread();
				thread.start();
			} else if (resultCode == RESULT_CANCELED) {
				appendMessage("Bluetooth was not enabled. Connection cancelled.");
			}
			break;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("log", mLogTextView.getText().toString());
	}

	public void alertBox(final String title, final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(BluetoothClientActivity.this).setTitle(title)
				.setMessage(message + " Press OK to exit.")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).show();
			}
		});
	}

	private void appendMessage(final String newText) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mLogTextView.append("\n" + newText);
				mScrollView.post(new Runnable() {
					@Override
					public void run() {
						mScrollView.fullScroll(View.FOCUS_DOWN);
					}
				});
			}
		});
	}

	private void unstickStartButton() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				appendMessage("");
				mStartButton.unstick();
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			}
		});
	}

	private class ConnectThread extends Thread {
		private final BluetoothAdapter mAdapter;
		private BluetoothSocket mSocket;

		public ConnectThread() {
			final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			mAdapter = manager.getAdapter();
			if (mAdapter == null) {
				appendMessage("ERROR: Bluetooth is not supported on this device.");
			} else if (!mAdapter.isEnabled()) {
				//Ask user to enable Bluetooth
				appendMessage("Bluetooth is not currently enabled. Attempting to enable...");
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				appendMessage("Attempting socket creation...");
				final BluetoothDevice device = mAdapter.getRemoteDevice(MAC_ADDRESS);
				try {
					mSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
					appendMessage("Socket created! Attempting socket connection...");
				} catch (IOException e) {
					e.printStackTrace();
					appendMessage("ERROR: Socket creation failed.");
					unstickStartButton();
				}
			}
		}

		@Override
		public void run() {
			if (mSocket != null) {
				mAdapter.cancelDiscovery();
				try {
					mSocket.connect();
				} catch (IOException connectException) {
					connectException.printStackTrace();
					try {
						connectException.printStackTrace();
						appendMessage("ERROR: Socket connection failed. Attempting to close socket...");
						mSocket.close();
						appendMessage("Socket closed.");
					} catch (IOException closeException) {
						closeException.printStackTrace();
						appendMessage("ERROR: Socket closing failed.");
					}
					appendMessage("Ensure that the server is up and try again.");
					unstickStartButton();
					return;
				}
				appendMessage("Socket connection sucessful!");
				DataExchangeThread thread = new DataExchangeThread("From Android with love", mSocket);
				thread.start();
			}
		}
	}

	private class DataExchangeThread extends Thread {
		private String mMessage;
		private BluetoothSocket mSocket;

		public DataExchangeThread(String message, BluetoothSocket socket) {
			mMessage = message;
			mSocket = socket;
		}

		@Override
		public void run() {
			if (mSocket != null) {
				appendMessage("Attempting to send data to server. Creating output stream...");
				OutputStream outStream = null;
				byte[] messageBytes = (mMessage == null ? "\n".getBytes() : (mMessage + "\n").getBytes());
				appendMessage("Output stream created! Sending message (" +
						(mMessage == null ? "no message provided!" : mMessage) + ") to server...");
				try {
					outStream = mSocket.getOutputStream();
				} catch (IOException e) {
					e.printStackTrace();
					appendMessage("ERROR: Output stream creation failed. Ensure that the server is up and try again.");
					unstickStartButton();
					return;
				}

				try {
					outStream.write(messageBytes);
				} catch (IOException e) {
					e.printStackTrace();
					if (MAC_ADDRESS.equals("00:00:00:00:00:00")) {
						appendMessage("ERROR: Message sending failed. Change the MAC address from 00:00:00:00:00:00 to the server's MAC address.");
					} else {
						appendMessage("ERROR: Message sending failed. Ensure that the server is up and try again.");
					}
					unstickStartButton();
					return;
				}

				appendMessage("Message sent! Preparing for server response...");
				InputStream inStream = null;
				try {
					inStream = mSocket.getInputStream();
				} catch (IOException e) {
					e.printStackTrace();
					appendMessage("ERROR: Input stream creation failed. Ensure that the server is up and try again.");
					unstickStartButton();
					return;
				}
				BufferedReader serverReader = new BufferedReader(new InputStreamReader(inStream));
				String response = null;
				try {
					/**
					 * WARNING! If the Android device is not connected to the server by this point,
					 * calling readLine() will crash the app without throwing an exception!
					 */
					response = serverReader.readLine();
				} catch (IOException e) {
					e.printStackTrace();
					appendMessage("ERROR: Failed to read server response. Ensure that the server is up and try again.");
					unstickStartButton();
					return;
				}

				appendMessage("Response from server: " + response);
				try {
					outStream.flush();
					outStream.close();
					inStream.close();
					mSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
					appendMessage("ERROR: Closing something failed.");
				}

				unstickStartButton();
			}
		}
	}
}