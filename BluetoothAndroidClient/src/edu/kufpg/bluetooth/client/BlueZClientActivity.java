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

public class BlueZClientActivity extends Activity {
	// Well known SPP UUID
	public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	public static final String MESSAGE = "From Android with love";
	public static final int REQUEST_ENABLE_BT = 8675309;
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private TextView mLogTextView;
	private StickyButton mStartButton;
	private Button mClearTextButton;
	private ScrollView mScrollView;
	private BluetoothAdapter mAdapter;
	private BluetoothDevice mDevice;

	@SuppressLint({ "NewApi", "InlinedApi" })
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
				if (mAdapter == null) {
					appendMessage("FATAL ERROR: Bluetooth is not supported on this device.");
				} else if (!mAdapter.isEnabled()) {
					//Ask user to enable Bluetooth
					appendMessage("Bluetooth is not currently enabled. Attempting to enable...");
					Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
				} else {
					if (mDevice == null) {
						Intent serverIntent = new Intent(BlueZClientActivity.this, DeviceListActivity.class);
						startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
					} else {
						new BluetoothRequestTask(BlueZClientActivity.this, mDevice).execute(MESSAGE);
					}
				}
			}
		});
		mClearTextButton = (Button) findViewById(R.id.button_clear_text);
		mClearTextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mLogTextView.setText("");
			}
		});

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			mAdapter = BluetoothAdapter.getDefaultAdapter();
		} else {
			final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			mAdapter = manager.getAdapter();
		}

		if (savedInstanceState != null) {
			mLogTextView.setText(savedInstanceState.getString("log"));
			mDevice = savedInstanceState.getParcelable("device");
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == RESULT_OK) {
				appendMessage("Bluetooth enabled!");
				//new BluetoothTask(this).execute(MESSAGE);
			} else if (resultCode == RESULT_CANCELED) {
				appendMessage("Bluetooth was not enabled. Connection cancelled.");
				unstickStartButton();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras()
						.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BluetoothDevice object
				mDevice = mAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				new BluetoothRequestTask(this, mDevice).execute(MESSAGE);
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
		outState.putParcelable("device", mDevice);
		((NoGuavaBaseApplication<BlueZClientActivity>) getApplication()).detachActivity(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onResume() {
		super.onResume();
		((NoGuavaBaseApplication<BlueZClientActivity>) getApplication()).attachActivity(this);
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

	private class BluetoothRequestTask extends AsyncActivityTask<BlueZClientActivity, String, String, String> {
		private BluetoothDevice mDevice;
		private BluetoothSocket mSocket;
		private OutputStream mOutStream;
		private InputStream mInStream;

		public BluetoothRequestTask(BlueZClientActivity activity, BluetoothDevice device) {
			super(activity);
			mDevice = device;
		}

		@Override
		protected String doInBackground(String... params) {
			publishProgress("Attempting socket creation...");
			try {
				mSocket = mDevice.createRfcommSocketToServiceRecord(BlueZClientActivity.MY_UUID);
				publishProgress("Socket created! Attempting socket connection...");
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Socket creation failed.");
				return null;
			}

			mAdapter.cancelDiscovery();
			try {
				mSocket.connect();
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Socket connection failed. Ensure that the server is up and try again.");
				return null;
			}

			publishProgress("Attempting to send data to server. Creating output stream...");
			String message = params[0];
			if (message == null) {
				message = "No message provided!";
			}
			byte[] messageBytes = message.getBytes();
			publishProgress("Output stream created! Sending message (" + message + ") to server...");
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
				publishProgress("ERROR: Message sending failed. Ensure that the server is up and try again.");
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
			char[] buffer = new char[5000];
			try {
				/**
				 * WARNING! If the Android device is not connected to the server by this point,
				 * calling read() will crash the app without throwing an exception!
				 */
				serverReader.read(buffer);
				response = new String(buffer);
			} catch (IOException e) {
				e.printStackTrace();
				publishProgress("ERROR: Failed to read server response. Ensure that the server is up and try again.");
				return null;
			}

			return response;
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
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result != null) {
				getActivity().appendMessage("Response from server: " + result);
			}
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