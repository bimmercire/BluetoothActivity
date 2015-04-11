package com.example.eric.bluetoothactivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class BluetoothActivity extends ActionBarActivity {

    private static final String TAG = "BluetoothActivity";

    private static final int REQUEST_CONNECT_DEVICE     =   1;
    private static final int REQUEST_ENABLE_BT          =   2;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private String mConnectedDeviceName                 = null;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter          = null;
    private BluetoothActivityService mBlueService       = null;

    private Button ledBtn, servoBtn, distanceBtn, resetBtn;
    private TextView letterViewer;
    private String messageToLetter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(mBluetoothAdapter == null){
            Toast.makeText(getApplicationContext(), "Bluetooth is off", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    public void onStart(){
        super.onStart();
        if(!mBluetoothAdapter.isEnabled()){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }else{
            if(mBlueService == null){
                setupBlue();
                Log.d(TAG, "start BLUE");
            }
        }
    }

    public synchronized void onResume(){
        super.onResume();
        if(mBlueService != null){
            if(mBlueService.getState() == BluetoothActivityService.STATE_NONE){
                mBlueService.start();
            }
        }
    }

    private void setupBlue(){
        Log.d(TAG, "Setup Blue");

        ledBtn = (Button) findViewById(R.id.ledBtn);
        servoBtn = (Button) findViewById(R.id.servoBtn);
        distanceBtn = (Button) findViewById(R.id.distanceBtn);
        resetBtn = (Button) findViewById(R.id.resetBtn);
        letterViewer = (TextView) findViewById(R.id.letterViewer);

        ledBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBTData("c");
                //letterViewer.setText("LED ON")
                Toast.makeText(getBaseContext(), "Clicked LED", Toast.LENGTH_SHORT).show();
            }
        });
        servoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBTData("a");
                Toast.makeText(getBaseContext(), "Servo Control", Toast.LENGTH_SHORT).show();
            }
        });
        distanceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBTData("b");
                Toast.makeText(getBaseContext(), "Distance Sensing", Toast.LENGTH_SHORT).show();
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBTData("e");
                Toast.makeText(getBaseContext(), "Reset", Toast.LENGTH_SHORT).show();
            }
        });

        mBlueService = new BluetoothActivityService(this, mHandler);
    }

    private void sendBTData(String message){
        if(mBlueService.getState() != BluetoothActivityService.STATE_CONNECTED){
            Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }
        if(message.length() > 0){
            byte[] send = message.getBytes();
            mBlueService.write(send);
        }
    }

    public synchronized void onPause(){
        super.onPause();
    }

    public void onStop(){
        super.onStop();
    }

    public void onDestroy(){
        super.onDestroy();
        if(mBlueService != null) mBlueService.stop();
    }

    private void ensureDiscoverable(){
        if(mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void messageReceivedBT(String message){
        if(message.equals("ON")){
            letterViewer.setText("LED is ON");
        }else if(message.equals("FF")){
            letterViewer.setText("LED is OFF");
        }

    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothActivityService.STATE_CONNECTED:
                            Log.d(TAG, "STATE Connected");
                            break;
                        case BluetoothActivityService.STATE_CONNECTING:
                            Log.d(TAG, "STATE Connecting");
                            break;
                        case BluetoothActivityService.STATE_LISTEN:
                        case BluetoothActivityService.STATE_NONE:
                            Log.d(TAG, "STATE Listen or none");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    Log.d(TAG, "writeMessage: " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d(TAG, "readMessage: " + readMessage);
                    if(readMessage.length() > 0){
                        if(readMessage.length() < 2){
                            messageToLetter += readMessage;
                        }else if(readMessage.length() == 2){
                            messageToLetter = readMessage;
                        }

                        if(messageToLetter.length() == 2){
                            Log.d(TAG, "MESSAGETOLETTER: " + messageToLetter);
                            messageReceivedBT(messageToLetter);
                            messageToLetter = "";
                        }
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


    public void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.d(TAG, "Inside onActivityResult: " + resultCode + "requestCode: " + requestCode);
        switch(requestCode){
            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK){
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mBlueService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK){
                    setupBlue();
                } else {
                    Toast.makeText(this," Bt not enabled", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch(id){
            case R.id.scan:
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
