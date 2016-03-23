package com.example.android.bluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = "MainActivity";
    private BluetoothAdapter BTAdapter;
    private int REQUEST_BLUETOOTH = 7;
    TextView myLabel;
    public static UUID applicationUUID = UUID.fromString("5A2AD47A-3A69-B37B-2161-B23971F8E066");

    //Read data multi-threading variables
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    volatile boolean isUIAlreadySet;

    public Handler bluetoothIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Set UUID in sharedPreference and ask only on installation
        SharedPreferences prefs = getSharedPreferences("MY_PREFS_NAME", MODE_PRIVATE);
        Boolean isFirst = prefs.getBoolean("isFirst", true);
        if (isFirst) {
            Intent intent = new Intent(this, insertUUID.class);
            startActivity(intent);
        } else if (!prefs.getString("UUIDString", "null").equals("null")) {
            applicationUUID = UUID.fromString(prefs.getString("UUIDString", "null"));
        }

        //Initialize BluetoothAdapter and start looking for Devices
        BTAdapter = BluetoothAdapter.getDefaultAdapter();


        //Set bluetooth on
        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        //Inflate textView to local variable
        myLabel = (TextView) findViewById(R.id.textview);
        myLabel.setText("WAITING");
        myLabel.setTextColor(Color.BLACK);

        //Start discover devices
        BTAdapter.startDiscovery();


        /** BroadcastReceiver for 3 different filters -
         * 1)@ACTION_BOND_STATE_CHANGED - if any device pair/unpair .@BOND_BONDED - device are bonded (Paired together).
         * 2)@ACTION_FOUND - New device was found while scanning.(StartDiscovery method)
         * 3)@ACTION_PAIRING_REQUEST - New pairing request was initialized by an outside device
         */

        BroadcastReceiver myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                //http://stackoverflow.com/questions/17541410/is-it-possible-to-get-notified-in-the-application-when-bluetooth-connects-to-my?rq=1
                String action = intent.getAction();
                //New device is found -
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.e("onReceive", "ACTION_BOND_STATE_CHANGED");
                    //the device are paired - can now be connected
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        // CONNECT
                        Log.e("onReceive", "BOND_BONDED");
                        //Initialize Rfcomm Socket to connect wih paired device

                        mmDevice = device;
                        try {
                            openBT();
                        } catch (IOException e) {
                            e.printStackTrace();

                            //update UI
                            MainActivity.this.updateUIFromReceiver();

                            createNotification("Failed to connect","Failed while trying to connect to paired device");
                            //close all connections
                            try {
                                closeBT();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            Log.e("openBT", "Faile" + e);
                        }
//                        BluetoothSocket bTSocket = null;
//                        try {
//                            //Try initiate socket
//                            bTSocket = device.createRfcommSocketToServiceRecord(applicationUUID);
//                        } catch (IOException e) {
//                            Log.e("--onReceive","BOND_BONDED, createRfcommSocketToServiceRecord faile" + e);
//                            createNotification("Can not connect to paired device",
//                                    "manged to pair the device but can not create socket from UUID" + e);
//                        }
//                        //Try connect the socket
//                        try {
//                            bTSocket.connect();
//                            mmOutputStream = bTSocket.getOutputStream();
//                            mmInputStream = bTSocket.getInputStream();
//                        } catch (IOException e) {
//                            Log.e("--onReceive", "BOND_BONDED, createRfcommSocketToServiceRecord Could not connect: " + e.toString());
//                            createNotification("Can not connect to paired device",
//                                    "manged to pair the device but can not create socket from UUID" + e);
//                        }
                    }
                }


                //New Device was found while scanning.(by StartDiscovery method)
                else if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Discover new device
                    Log.wtf("onReceive", "ACTION_FOUND");

                    //Try to automatically pair with the new device
                    Boolean isBonded = false;

                    try {
                        isBonded = createAutoPair(device);
                        Log.wtf("onReceive", " isBonded: " + isBonded);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.wtf("--onReceive", " isBonded,Failed to create autopair " + e);
                        createNotification("Can not auto-pair the device",
                                "Try to pair the device and failed: " + e);
                    }
                }

                //Request for PAIRING was made by an outside device
                else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                    Log.e("onReceive", "ACTION_PAIRING_REQUEST");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    //Automatically set pin
                    setBluetoothPairingPin(device);
                    Log.e("onReceive", "setBluetoothPairingPin");


                }
            }
        };

        //Register new broadcast receiver with 3 intent-filters (Documented above onReceive())
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        getApplicationContext().registerReceiver(myReceiver, intentFilter);


    }

    private void updateUIFromReceiver() {

        //Sets UI
        myLabel = (TextView) findViewById(R.id.textview);
        myLabel.setText("FAIL");
        myLabel.setTextColor(Color.RED);
    }

    /**
     * Automatically create bond by invoking  "createBond" Method from Bluetooth device class
     */
    public boolean createAutoPair(BluetoothDevice btDevice)
            throws Exception {
        Class class1 = Class.forName("android.bluetooth.BluetoothDevice");
        Method createBondMethod = class1.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);

        return returnValue.booleanValue();

    }

    /**
     * Automatically set user pin to "000000" by invoking setPairingConfirmation method and set current device from
     * onReceive ACTION_PAIRING_REQUEST filter.
     * and then cancel user UI dialog by invoking "cancelPairingUserInput"
     * With the help of:
     * http://stackoverflow.com/questions/19047995/programmatically-pair-bluetooth-device-without-the-user-entering-pin
     */
    public void setBluetoothPairingPin(BluetoothDevice device) {
        String pinString = "000000";
        byte[] pinBytes = pinString.getBytes(StandardCharsets.UTF_8);
        try {
            Log.d(TAG, "Try to set the PIN");
            Method m = device.getClass().getMethod("setPin", byte[].class);
            m.invoke(device, pinBytes);
            Log.d(TAG, "Success to add the PIN.");
            try {
                device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
                device.getClass().getMethod("cancelPairingUserInput").invoke(device);
                Log.d("onReceive", " ACTION_PAIRING_REQUEST, Success to setPairingConfirmation.");
            } catch (Exception e) {

                Log.e("--onReceive", " ACTION_PAIRING_REQUEST, setBluetoothPairingPin" + e.getMessage());
                createNotification("Can not auto-pair the device",
                        "Try to pair the device and failed: " + e);
            }
        } catch (Exception e) {
            Log.e("--onReceive", "setBluetoothPairingPin" + e.getMessage());
            createNotification("Can not auto-set pin 000000",
                    "Try to set pin and failed: " + e);
        }
    }

    //For UnPairing - http://stackoverflow.com/questions/14834318/android-how-to-pair-bluetooth-devices-programmatically
    private void unpairDevice(BluetoothDevice device) {
        Log.wtf("here", "--------------------------------------------");

        try {
            Log.d("unpairDevice()", "Start Un-Pairing...");
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Log.d("unpairDevice()", "Un-Pairing finished.");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }


    //Build notification for Debug mode
    private void createNotification(String errorTitle, String errorBody) {


        Notification n = new Notification.Builder(this)
                .setContentTitle(errorTitle)
                .setContentText(errorBody)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(new Notification.BigTextStyle().bigText(errorBody))
                .setAutoCancel(true).build();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notificationManager.notify(0, n);
    }


    void openBT() throws IOException {

        mmSocket = mmDevice.createRfcommSocketToServiceRecord(applicationUUID);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        sendData();

    }

    void sendData() throws IOException {
        String msg = "Ping;";
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        //Send msg String to output Stream
        mmOutputStream.write(b);

        //Begin listen to data for 150 milliseconds
        beginListenForData();

        Log.e("sentData: ", "bytes are :" + msg);
        createNotification("Data was sent to the other device", "Data was sent to the other device," +
                "The Data was :" + msg);


    }

    void beginListenForData() {
        final Handler handler = new Handler();

        stopWorker = false;
        isUIAlreadySet = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                long t = System.currentTimeMillis();
                long end = t + 150;
                long timeCounter = 0;

                //Check for no interruptions while working and for 150ml only
                while (!Thread.currentThread().isInterrupted() && !stopWorker && (System.currentTimeMillis() < end)) {
                    try {
                        //Check for current Input stream length available
                        int bytesAvailable = mmInputStream.available();

                        //If there is some inputStream
                        if (bytesAvailable > 0) {
                            //Set up new byte array to collect current stream
                            byte[] packetBytes = new byte[bytesAvailable];

                            //read from inputStream and insert to the new byte array
                            mmInputStream.read(packetBytes);

                            //For to build buffer
                            for (int i = 0; i < bytesAvailable; i++) {
                                //set one byte to be send to the buffer - if buffer is bigger then 3 Validate the answer
                                byte b = packetBytes[i];
                                if (readBufferPosition >= 3) {
                                    //build new byte array with the length of the buffer array
                                    byte[] encodedBytes = new byte[readBufferPosition];

                                    //http://stackoverflow.com/questions/12239692/android-inputstream-dropping-first-two-bytes-modified-bluetoothchat/12264498#12264498
                                    //copy buffer to length to prevent bug from above link
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    //Encode data from byte to final string using String.class method
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            if (data == "OK") {
                                                myLabel.setText("PASS");
                                                myLabel.setTextColor(Color.GREEN);
                                                isUIAlreadySet = true;

                                                //Close all connections
                                                try {
                                                    closeBT();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            } else {
                                                myLabel.setText("FAIL");
                                                myLabel.setTextColor(Color.RED);
                                                isUIAlreadySet = true;

                                                //Close all connections
                                                try {
                                                    closeBT();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Log.e(" Thread.sleep(1);", " Problem with current device system");
                            createNotification("Problem in cell phone", "Current device had " +
                                    "system problem in Android OS while trying to calculate time (milliseconds)");
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
                //UI not been set it - the BY device did not work
                if (!isUIAlreadySet) {

                    //set UI for fail
                    handler.post(new Runnable() {

                        public void run() {
                            myLabel.setText("FAIL");
                            myLabel.setTextColor(Color.RED);
                            isUIAlreadySet = true;
                            try {
                                //Close BT connections
                                closeBT();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }

            }

        });

        workerThread.start();
    }

    void closeBT() throws IOException {
        stopWorker = true;

        unpairDevice(mmDevice);


        //Schedule Task for 5 seconds on,cunt time on background thread
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        //Restart the app
                        Intent intent2 = getIntent();
                        finish();
                        startActivity(intent2);
                    }
                }, 10000);

    }

    public void refreshAppOnClick(View view) {

        //Restart the app
        Intent intent = getIntent();
        finish();
        startActivity(intent);

    }
}





