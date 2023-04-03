package com.example.controautobt1;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private String deviceName = null;
    private String deviceAddress;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;

    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private Button buttonConnect;
    private TextView textViewInfo;


    // System sensor manager instance.
        private SensorManager mSensorManager;

        // Accelerometer and magnetometer sensors, as retrieved from the
        // sensor manager.
        private Sensor mSensorAccelerometer;
        private Sensor mSensorMagnetometer;

        // Current data from accelerometer & magnetometer.  The arrays hold values
        // for X, Y, and Z.
        private float[] mAccelerometerData = new float[3];
        private float[] mMagnetometerData = new float[3];

        // TextViews to display current sensor values.
        private TextView mTextSensorAzimuth;
        private TextView mTextSensorPitch;
        private TextView mTextSensorRoll;

        // ImageView drawables to display spots.
        private ImageView mSpotTop;
        private ImageView mSpotBottom;
        private ImageView mSpotLeft;
        private ImageView mSpotRight;

        // System display. Need this for determining rotation.
        private Display mDisplay;

        // Very small values for the accelerometer (on all three axes) should
        // be interpreted as 0. This value is the amount of acceptable
        // non-zero drift.
        private static final float VALUE_DRIFT = 0.05f;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            mTextSensorAzimuth = (TextView) findViewById(R.id.value_azimuth);
            mTextSensorPitch = (TextView) findViewById(R.id.value_pitch);
            mTextSensorRoll = (TextView) findViewById(R.id.value_roll);
            mSpotTop = (ImageView) findViewById(R.id.spot_top);
            mSpotBottom = (ImageView) findViewById(R.id.spot_bottom);
            mSpotLeft = (ImageView) findViewById(R.id.spot_left);
            mSpotRight = (ImageView) findViewById(R.id.spot_right);
// bluetooth objets
            buttonConnect = findViewById(R.id.buttonConnect);
            final Toolbar toolbar = findViewById(R.id.toolbar);
            final ProgressBar progressBar = findViewById(R.id.progressBar);
            progressBar.setVisibility(View.GONE);
            textViewInfo = findViewById(R.id.textViewInfo);
            final Button buttonToggle = findViewById(R.id.buttonToggle);
            buttonToggle.setEnabled(false);
            final ImageView imageView = findViewById(R.id.imageView);
            imageView.setBackgroundColor(getResources().getColor(R.color.colorOff));

    //bluetooth code

            // If a bluetooth device has been selected from SelectDeviceActivity
            deviceName = getIntent().getStringExtra("deviceName");
            if (deviceName != null) {
                // Get the device address to make BT Connection
                deviceAddress = getIntent().getStringExtra("deviceAddress");
                // Show progree and connection status
                toolbar.setSubtitle("Connecting to " + deviceName + "...");
                progressBar.setVisibility(View.VISIBLE);
                buttonConnect.setEnabled(false);

            /*
            This is the most important piece of code. When "deviceName" is found
            the code will call a new thread to create a bluetooth connection to the
            selected device (see the thread code below)
             */
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                createConnectThread = new CreateConnectThread(bluetoothAdapter, deviceAddress);
                createConnectThread.start();
            }

        /*
        Second most important piece of Code. GUI Handler
         */
            handler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case CONNECTING_STATUS:
                            switch (msg.arg1) {
                                case 1:
                                    toolbar.setSubtitle("Connected to " + deviceName);
                                    progressBar.setVisibility(View.GONE);
                                    buttonConnect.setEnabled(true);
                                    buttonToggle.setEnabled(true);
                                    break;
                                case -1:
                                    toolbar.setSubtitle("Device fails to connect");
                                    progressBar.setVisibility(View.GONE);
                                    buttonConnect.setEnabled(true);
                                    break;
                            }
                            break;

                        case MESSAGE_READ:
                            String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                            textViewInfo.setText("Arduino Message : " + arduinoMsg);
                            switch (arduinoMsg.toLowerCase()) {
                                case "led is turned on":
                                    imageView.setBackgroundColor(getResources().getColor(R.color.colorOn));
                                    textViewInfo.setText("Arduino Message : " + arduinoMsg);
                                    break;
                                case "led is turned off":
                                    imageView.setBackgroundColor(getResources().getColor(R.color.colorOff));
                                    textViewInfo.setText("Arduino Message : " + arduinoMsg);
                                    break;
                            }
                            break;
                    }
                }
            };

            // Select Bluetooth Device
            buttonConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Move to adapter list
                    Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                    startActivity(intent);
                }
            });

            // Button to ON/OFF LED on Arduino Board
            buttonToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String cmdText = null;
                    String btnState = buttonToggle.getText().toString().toLowerCase();
                    switch (btnState) {
                        case "turn on":
                            buttonToggle.setText("Turn Off");
                            // Command to turn on LED on Arduino. Must match with the command in Arduino code
                            cmdText = "<turn on>";
                            break;
                        case "turn off":
                            buttonToggle.setText("Turn On");
                            cmdText = "<turn off>";
                            break;
                    }
                    // Send command to Arduino board
                    connectedThread.write(cmdText);
                }
            });



            // Get accelerometer and magnetometer sensors from the sensor manager.
            // The getDefaultSensor() method returns null if the sensor
            // is not available on the device.
            mSensorManager = (SensorManager) getSystemService(
                    Context.SENSOR_SERVICE);
            mSensorAccelerometer = mSensorManager.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER);
            mSensorMagnetometer = mSensorManager.getDefaultSensor(
                    Sensor.TYPE_MAGNETIC_FIELD);

            // Get the display from the window manager (for rotation).
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            mDisplay = wm.getDefaultDisplay();


        }

        /**
         * Listeners for the sensors are registered in this callback so that
         * they can be unregistered in onStop().
         */
        @Override
        protected void onStart() {
            super.onStart();

            // Listeners for the sensors are registered in this callback and
            // can be unregistered in onStop().
            //
            // Check to ensure sensors are available before registering listeners.
            // Both listeners are registered with a "normal" amount of delay
            // (SENSOR_DELAY_NORMAL).
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (mSensorMagnetometer != null) {
                mSensorManager.registerListener(this, mSensorMagnetometer,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        @Override
        protected void onStop() {
            super.onStop();

            // Unregister all sensor listeners in this callback so they don't
            // continue to use resources when the app is stopped.
            mSensorManager.unregisterListener(this);
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            // The sensor type (as defined in the Sensor class).
            int sensorType = sensorEvent.sensor.getType();

            // The sensorEvent object is reused across calls to onSensorChanged().
            // clone() gets a copy so the data doesn't change out from under us
            switch (sensorType) {
                case Sensor.TYPE_ACCELEROMETER:
                    mAccelerometerData = sensorEvent.values.clone();
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mMagnetometerData = sensorEvent.values.clone();
                    break;
                default:
                    return;
            }
            // Compute the rotation matrix: merges and translates the data
            // from the accelerometer and magnetometer, in the device coordinate
            // system, into a matrix in the world's coordinate system.
            //
            // The second argument is an inclination matrix, which isn't
            // used in this example.
            float[] rotationMatrix = new float[9];
            boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                    null, mAccelerometerData, mMagnetometerData);

            // Remap the matrix based on current device/activity rotation.
            float[] rotationMatrixAdjusted = new float[9];
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_0:
                    rotationMatrixAdjusted = rotationMatrix.clone();
                    break;
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                            rotationMatrixAdjusted);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                            rotationMatrixAdjusted);
                    break;
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                            rotationMatrixAdjusted);
                    break;
            }

            // Get the orientation of the device (azimuth, pitch, roll) based
            // on the rotation matrix. Output units are radians.
            float orientationValues[] = new float[3];
            if (rotationOK) {
                SensorManager.getOrientation(rotationMatrixAdjusted,
                        orientationValues);
            }

            // Pull out the individual values from the array.
            float azimuth = orientationValues[0];
            float pitch = orientationValues[1];
            float roll = orientationValues[2];

            // Pitch and roll values that are close to but not 0 cause the
            // animation to flash a lot. Adjust pitch and roll to 0 for very
            // small values (as defined by VALUE_DRIFT).
            if (Math.abs(pitch) < VALUE_DRIFT) {
                pitch = 0;
            }
            if (Math.abs(roll) < VALUE_DRIFT) {
                roll = 0;
            }

            // Fill in the string placeholders and set the textview text.
            mTextSensorAzimuth.setText(getResources().getString(
                    R.string.value_format, azimuth));
            mTextSensorPitch.setText(getResources().getString(
                    R.string.value_format, pitch));
            mTextSensorRoll.setText(getResources().getString(
                    R.string.value_format, roll));

            // Reset all spot values to 0. Without this animation artifacts can
            // happen with fast tilts.
            mSpotTop.setAlpha(0f);
            mSpotBottom.setAlpha(0f);
            mSpotLeft.setAlpha(0f);
            mSpotRight.setAlpha(0f);

            // Set spot color (alpha/opacity) equal to pitch/roll.
            // this is not a precise grade (pitch/roll can be greater than 1)
            // but it's close enough for the animation effect.
            //connectedThread.write(String.valueOf(pitch));
            //connectedThread.write(String.valueOf(roll));
            if(connectedThread!=null) {
                connectedThread.write("pitch");
                connectedThread.write(String.valueOf(pitch));
                connectedThread.write("roll");
                connectedThread.write(String.valueOf(roll));
            }
            if (pitch > 0) {
                mSpotBottom.setAlpha(pitch);
            } else {
                mSpotTop.setAlpha(Math.abs(pitch));
            }
            if (roll > 0) {
                mSpotLeft.setAlpha(roll);
            } else {
                mSpotRight.setAlpha(Math.abs(roll));
            }
        }

        /**
         * Must be implemented to satisfy the SensorEventListener interface;
         * unused in this app.
         */
        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }


    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public static class CreateConnectThread extends Thread {

        @SuppressLint("MissingPermission")
        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;

            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                Log.e("Status", "Device connected");
                handler .obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;
                    if (buffer[bytes] == '\n'){
                        readMessage = new String(buffer,0,bytes);
                        Log.e("Arduino Message",readMessage);
                        handler.obtainMessage(MESSAGE_READ,readMessage).sendToTarget();
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error","Unable to send message",e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null){
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }
}
