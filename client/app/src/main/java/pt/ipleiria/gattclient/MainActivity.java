package pt.ipleiria.gattclient;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity {

    ExpandableListAdapter mExpandableListAdapter;
    ExpandableListView mExpandableListView;
    List<String> mExpandableListGroups;
    HashMap<String, List<String>> mExpandableListChildMap;
    TextView textViewLog;
    Button scanButton,clearButton; //buttons
    final String GROUP_DEVICE = "BLE Device Addresses";
    private GattClientService mGattClientService = null;
    private boolean mBound = false;

    private GattProfile mGattProfile;
    private GattClientService.GattCallbackInterface mBLEInterface = new GattClientService.GattCallbackInterface() {
        @Override
        public void onGattDeviceFound(final BluetoothDevice device, final String id) {
            Log.i("GattCallbackInterface:", "Found Device:\n\tADDR : " + device.getAddress());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDeviceFound(device, id);
                }
            });
        }
        @Override
        public void onGattServerConnected(final BluetoothGatt bluetoothGatt){
            Log.i("GattCallbackInterface:", "Connected to Device:\n\tADDR : " + bluetoothGatt.getDevice().getAddress());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDeviceConnected(bluetoothGatt);
                }
            });
    }
        @Override
        public void onGattServerDisconnected(final BluetoothGatt bluetoothGatt){
            Log.i("GattCallbackInterface:", "Disconnected from Device:\n\tADDR : " + bluetoothGatt.getDevice().getAddress());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDeviceDisconnected(bluetoothGatt);
                }
            });
    }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(!checkPermissions()) {//if permissions are not enabled start app settings activity
            Toast.makeText(this, "Please Enable this App's Permissions in Application Manager", Toast.LENGTH_LONG).show();
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null); //open this package
            intent.setData(uri); //send this app package as an argument to settings intent
            startActivity(intent); //start settings activity
        }
        textViewLog =  ((TextView)findViewById(R.id.textViewLog));
        textViewLog.setMovementMethod(new ScrollingMovementMethod());



        scanButton = ((Button) findViewById(R.id.buttonScan));
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGattClientService != null) {
                    mGattClientService.scanLeDevice(!mGattClientService.isScanning()); //toggle by NOTting current scan status
                    if(!mGattClientService.isBluetoothEnabled()){ //if not enabled show user
                        Log.i("MainActivity", "Bluetooth Not Enabled");
                        Toast.makeText(v.getContext(), "Bluetooth Not Enabled!", Toast.LENGTH_SHORT).show();
                    }
                    if (mGattClientService.isScanning())//if scanning show button text as
                        scanButton.setText("Stop Scan");
                    else
                        scanButton.setText("Start Scan");
                }


            }
        });
        clearButton = ((Button) findViewById(R.id.buttonClear));

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mGattClientService != null) mGattClientService.clearAvailableDevices();
                for(String s: mExpandableListGroups){
                    mExpandableListChildMap.get(s).clear();
                }
                mExpandableListAdapter.notifyDataSetChanged();

                if(textViewLog.getEditableText() != null)textViewLog.getEditableText().clear();
                textViewLog.bringPointIntoView(0);
            }
        });

        mGattProfile = new GattProfile();
        mExpandableListGroups = new ArrayList<String>();
        mExpandableListChildMap = new HashMap<String, List<String>>();
        // Adding map data
        mExpandableListGroups.add(GROUP_DEVICE);
        mExpandableListChildMap.put(GROUP_DEVICE, new ArrayList<String>());


        mExpandableListAdapter = new ExpandableListAdapter(this, mExpandableListGroups, mExpandableListChildMap);

        mExpandableListView = (ExpandableListView) findViewById(R.id.expandableListView);
        // setting list adapter
        mExpandableListView.setAdapter(mExpandableListAdapter);
        for(int i=0; i< mExpandableListGroups.size(); i++)
            mExpandableListView.expandGroup(i);
        mExpandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int listPosition, long id) {
                String group = mExpandableListGroups.get(groupPosition);
                if(mGattClientService != null){
                    if(group != null){
                        String data = mExpandableListChildMap.get( mExpandableListGroups.get(groupPosition))
                                .get(listPosition);

                        Toast.makeText(parent.getContext(), group + ":" + data, Toast.LENGTH_SHORT).show();
                        Log.i("ExpandableListView",group + ":" + data);

                        if(group.equals(GROUP_DEVICE)){
                            textViewLog.append("\nTrying to Connect to Device Addr:" + data + "...\n");
                            mGattClientService.connectToDevice(data);
                        }
                    }
                }else{
                    Log.e("ExpandableListView","OnClicked Child: GATT SERVICE NOT CONNECTED TO ACTIVITY!");
                }


                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();

        } else {
            // Bind to GattClientService
            Intent intent = new Intent(this, GattClientService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }


    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            if(mGattClientService != null) mGattClientService.clearAvailableDevices();
            mGattClientService.scanLeDevice(false);
            for(String s: mExpandableListGroups){
                mExpandableListChildMap.get(s).clear();
            }
            mExpandableListAdapter.notifyDataSetChanged();
            //unbindService(mConnection);
           // mBound = false;
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i("ServiceConnection", "GattClientService Connected to activity");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mGattClientService = ((GattClientService.BLEServiceBinder) service).getService();
            mGattClientService.setInterface(mBLEInterface);

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            Log.i("ServiceConnection", "GattClientService Disconnected from activity");

        }
    };

    private void onDeviceFound(final BluetoothDevice device, String id) {
        Log.i("MainActivity", "Found Device ADDR:" + id);
        //update data map
        int deviceIndex = mExpandableListGroups.indexOf(GROUP_DEVICE);
        List<String> childList = mExpandableListChildMap.get(mExpandableListGroups.get(deviceIndex));
        if(childList != null && device != null && deviceIndex >= 0 ){
            childList.add(device.getAddress());
            mExpandableListAdapter.notifyDataSetChanged();
        }
        mExpandableListAdapter.notifyDataSetChanged();
    }
    private void onDeviceConnected(final BluetoothGatt bluetoothGatt) {
        if (bluetoothGatt != null) {
            textViewLog.append("Connected to GATT Server on device:\n   " + bluetoothGatt.getDevice().getAddress() + "\n");
            //update data map

        }


    }
    private void onDeviceDisconnected(final BluetoothGatt bluetoothGatt) {
        if (bluetoothGatt != null) {
            textViewLog.append("Disconnected from device:\n   " + bluetoothGatt.getDevice().getAddress() + "\n");
           //update data map

            mExpandableListAdapter.notifyDataSetChanged();

        }
    }

    public boolean checkPermissions(){
            String permission = "android.permission.ACCESS_COARSE_LOCATION";
            int res = this.checkCallingOrSelfPermission(permission);
            return (res == PackageManager.PERMISSION_GRANTED);
    }

}

