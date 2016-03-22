package com.example.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = "MainActivity";
    private BluetoothAdapter BTAdapter;
    private BluetoothSocket mBluetoothSocket;
    public static UUID applicationUUID = UUID.fromString("00030000-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Set UUID in sharedPreference and ask only on installation
        SharedPreferences prefs = getSharedPreferences("MY_PREFS_NAME", MODE_PRIVATE);
        Boolean isFirst = prefs.getBoolean("isFirst", true);
        if (isFirst) {
            Intent intent = new Intent(this,insertUUID.class);
            startActivity(intent);
        }
        else if (!prefs.getString("UUIDString","null").equals("null")) {
            applicationUUID = UUID.fromString(prefs.getString("UUIDString", "null"));
        }

        //Initialize BluetoothAdapter and start loo looking for Devices
        BTAdapter = BluetoothAdapter.getDefaultAdapter();
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

                    //the device are paired - can now be connected
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        // CONNECT
                        Log.wtf("here", "--------------------device.getBondState() == BluetoothDevice.BOND_BONDED------------------------");

                        //Initialize Rfcomm Socket to connect wih paired device
                        BluetoothSocket bTSocket = null;
                        try {
                            //Try initiate socket
                            bTSocket = device.createRfcommSocketToServiceRecord(applicationUUID);
                        } catch (IOException e) {
                            Log.e("CONNECTTHREAD", "Could not create RFCOMM socket:" + e.toString());

                        }
                        //Try connect the socket
                        try {
                            bTSocket.connect();
                        } catch (IOException e) {
                            Log.e("CONNECTTHREAD", "Could not connect: " + e.toString());
                        }
                    }
                }


                //New Device was found while scanning.(by StartDiscovery method)
                else if (BluetoothDevice.ACTION_FOUND.equals(action))
                {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Discover new device
                    Log.wtf("here", "--------------------BluetoothDevice.ACTION_FOUND.equals(action)------------------------");

                    //Try to automatically pair with the new device
                    Boolean isBonded = false;
                    try {
                        isBonded = createAutoPair(device);
                        Log.wtf("here", "   " + isBonded);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                //Request for PAIRING was made by an outside device
                else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action))
                {
                    Log.wtf("here", "--------------------ACTION_PAIRING_REQUEST------------------------");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    //Todo:delete before releasing
                    //Automatically set pin
                    //setBluetoothPairingPin(device);
                    try {
                        BluetoothSocket bTSocket = device.createRfcommSocketToServiceRecord(applicationUUID);
                        Log.wtf("here", "--------------------------------------------");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        };

        //Register new broadcast receiver with 3 intent-filters (Documented above onReceive())
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        getApplicationContext().registerReceiver(myReceiver, intentFilter);




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

    /**Automatically set user pin to "000000" by invoking setPairingConfirmation method and set current device from
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
                Log.d(TAG, "Success to setPairingConfirmation.");
            } catch (Exception e) {

                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    //For UnPairing - http://stackoverflow.com/questions/14834318/android-how-to-pair-bluetooth-devices-programmatically
    private void unpairDevice(BluetoothDevice device) {
        try {
            Log.d("unpairDevice()", "Start Un-Pairing...");
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Log.d("unpairDevice()", "Un-Pairing finished.");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}

