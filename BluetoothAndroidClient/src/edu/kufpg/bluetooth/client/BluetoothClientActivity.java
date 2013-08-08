package edu.kufpg.bluetooth.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
	public static final String MESSAGE = "From Android with love";
	static final int REQUEST_ENABLE_BT = 8675309;
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
				new BluetoothTask(BluetoothClientActivity.this).execute(MESSAGE);
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
				new BluetoothTask(this).execute(MESSAGE);
			} else if (resultCode == RESULT_CANCELED) {
				appendMessage("Bluetooth was not enabled. Connection cancelled.");
			}
			break;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("log", mLogTextView.getText().toString());
		((NoGuavaBaseApplication<BluetoothClientActivity>) getApplication()).detachActivity(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onResume() {
		super.onResume();
		((NoGuavaBaseApplication<BluetoothClientActivity>) getApplication()).attachActivity(this);
	}

	void appendMessage(final String newText) {
		mLogTextView.append("\n" + newText);
		mScrollView.post(new Runnable() {
			@Override
			public void run() {
				mScrollView.fullScroll(View.FOCUS_DOWN);
			}
		});
	}

	void unstickStartButton() {
		appendMessage("");
		mStartButton.unstick();
	}

	private class BluetoothTask extends AsyncActivityTask<BluetoothClientActivity, String, String, Void> {
		private BluetoothSocket mSocket;
		private OutputStream mOutStream;
		private InputStream mInStream;

		public BluetoothTask(BluetoothClientActivity activity) {
			super(activity);
		}

		@SuppressLint({ "NewApi", "InlinedApi" })
		@Override
		protected Void doInBackground(String... params) {
			BluetoothAdapter adapter;

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
				adapter = BluetoothAdapter.getDefaultAdapter();
			} else {
				final BluetoothManager manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
				adapter = manager.getAdapter();
			}

			if (adapter == null) {
				publishProgress("ERROR: Bluetooth is not supported on this device.");
				return null;
			} else if (!adapter.isEnabled()) {
				//Ask user to enable Bluetooth
				publishProgress("Bluetooth is not currently enabled. Attempting to enable...");
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				getActivity().startActivityForResult(enableBtIntent, BluetoothClientActivity.REQUEST_ENABLE_BT);
				return null;
			} else {
				publishProgress("Attempting socket creation...");
				final BluetoothDevice device = adapter.getRemoteDevice(BluetoothClientActivity.MAC_ADDRESS);
				try {
					mSocket = device.createRfcommSocketToServiceRecord(BluetoothClientActivity.MY_UUID);
					publishProgress("Socket created! Attempting socket connection...");
				} catch (IOException e) {
					e.printStackTrace();
					publishProgress("ERROR: Socket creation failed.");
					return null;
				}
			}

			adapter.cancelDiscovery();
			try {
				mSocket.connect();
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Socket connection failed. Ensure that the server is up and try again.");
				return null;
			}

			publishProgress("Attempting to send data to server. Creating output stream...");
			String message = params[0];
			byte[] messageBytes = (message == null ? "\n".getBytes() : (message + "\n").getBytes());
			publishProgress("Output stream created! Sending message (" +
					(message == null ? "no message provided!" : message) + ") to server...");
			try {
				mOutStream = mSocket.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Output stream creation failed. Ensure that the server is up and try again.");
				return null;
			}

			try {
				mOutStream.write(messageBytes);
			} catch (IOException e) {
				e.printStackTrace();
				if (BluetoothClientActivity.MAC_ADDRESS.equals("00:00:00:00:00:00")) {
					publishProgress("ERROR: Message sending failed. Change the MAC address from 00:00:00:00:00:00 to the server's MAC address.");
				} else {
					publishProgress("ERROR: Message sending failed. Ensure that the server is up and try again.");
				}
				return null;
			}

			publishProgress("Message sent! Preparing for server response...");
			try {
				mInStream = mSocket.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Input stream creation failed. Ensure that the server is up and try again.");
				return null;
			}
			BufferedReader serverReader = new BufferedReader(new InputStreamReader(mInStream));
			String response = null;
			try {
				/**
				 * WARNING! If the Android device is not connected to the server by this point,
				 * calling readLine() will crash the app without throwing an exception!
				 */
				response = serverReader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Failed to read server response. Ensure that the server is up and try again.");
				return null;
			}

			publishProgress("Response from server: " + response);
			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			getActivity().appendMessage(progress[0]);
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			end();
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			end();
		}

		private void end() {
			try {
				if (mOutStream != null) {
					mOutStream.flush();
					mOutStream.close();
				}

				if (mInStream != null) {
					mInStream.close();
				}

				if (mSocket != null) {
					mSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Closing something failed.");
			}
			getActivity().unstickStartButton();
		}

	}
}