package com.example.eric.bluetoothactivity;

import android.bluetooth.BluetoothAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Created by Eric on 4/5/2015.
 */
public class BluetoothActivityService {

    private static final String TAG = "com.example.eric.bluetoothactivity.BluetoothActivityService";

    private static final UUID HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String NAME = "BluetoothActivity";
    private final BluetoothAdapter mBTAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;


    public static final int STATE_NONE          = 0;        //Doing Nothing
    public static final int STATE_LISTEN        = 1;        //Listening for INCOMING
    public static final int STATE_CONNECTING    = 2;        //Initiating OUTGOING connection
    public static final int STATE_CONNECTED     = 3;        //Connected to Remote Device

    public BluetoothActivityService(Context context, Handler handler){
        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    public synchronized void setState(int state){

        mState = state;

        mHandler.obtainMessage(BluetoothActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState(){
        return mState;
    }

    public synchronized void start(){
        Log.d(TAG, "Start");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mAcceptThread == null){
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    public synchronized void connect(BluetoothDevice device){
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        //Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    public synchronized void stop(){
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] string){
        ConnectedThread r;
        synchronized (this){
            if(mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(string);
    }

    private void connectionFailed(){
        setState(STATE_LISTEN);
        Message msg = mHandler.obtainMessage(BluetoothActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionLost() {
        setState(STATE_LISTEN);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private class AcceptThread extends Thread{
        //Local Server Socket
        private final BluetoothServerSocket mServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            try{
                tmp = mBTAdapter.listenUsingRfcommWithServiceRecord(NAME, HC05_UUID);
            }catch (IOException e){
                Log.e(TAG, "listen failed");
            }
            mServerSocket = tmp;
        }

        public void run(){
            setName("AcceptThread");
            BluetoothSocket socket = null;

            while(mState != STATE_CONNECTED){
                try{
                    socket = mServerSocket.accept();
                }catch (IOException e){
                    Log.e(TAG, "accept() failed", e);
                    break;
                }
                if(socket != null){
                    synchronized (BluetoothActivityService.this){
                        switch(mState){
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try{
                                    socket.close();
                                }catch (IOException e){
                                    Log.e(TAG, "Could not close unwanted socket");
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel(){
            try{
                mServerSocket.close();
            }catch (IOException e){
                Log.e(TAG, "close() of server failed", e);
            }
        }

    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(HC05_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }
        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");
            // Always cancel discovery because it will slow down a connection
            mBTAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                BluetoothActivityService.this.start();
                return;
            }
            // Reset the ConnectThread because we're done
            synchronized (BluetoothActivityService.this) {
                mConnectThread = null;
            }
            // Start the connected thread
            connected(mmSocket, mmDevice);
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BluetoothActivity.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }
        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothActivity.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}
