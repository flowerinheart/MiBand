package com.martindisch.accelerometer;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.martindisch.accelerometer.Model.Profile;

import java.util.HashMap;
import java.util.UUID;

public class BluetoothIO extends BluetoothGattCallback {
    private static final String TAG = "BluetoothIO";
    BluetoothGatt gatt;
    HashMap<UUID, NotifyListener> notifyListeners = new HashMap<>();
    NotifyListener disconnectedListener = null;


    public interface NotifyListener {
        public void onNotify(byte[] data);
    }
    public interface RealtimeStepsNotifyListener {
        public void onNotify(int steps);
    }
    public interface RealtimeSensorNotifyListener {
        public void onNotify(double[][] axis);
    }

    public void connect(final Context context, BluetoothDevice device) {
        device.connectGatt(context, false, BluetoothIO.this);
    }

    public void setDisconnectedListener(NotifyListener disconnectedListener) {
        this.disconnectedListener = disconnectedListener;
    }

    public BluetoothDevice getDevice() {
        if (null == gatt) {
            Log.e(TAG, "connect to miband first");
            return null;
        }
        return gatt.getDevice();
    }


    public void writeCharacteristic(UUID characteristicUUID, byte[] value) {
        writeCharacteristic(Profile.UUID_SERVICE_MILI, characteristicUUID, value);
    }

    public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value) {
        try {
            if (null == gatt) {
                Log.e(TAG, "connect to miband first");
                throw new Exception("connect to miband first");
            }
            BluetoothGattCharacteristic chara = gatt.getService(serviceUUID).getCharacteristic(characteristicUUID);
            if (null == chara) {
                this.onFail(-1, "BluetoothGattCharacteristic " + characteristicUUID + " is not exsit");
                return;
            }
            chara.setValue(value);
            if (false == this.gatt.writeCharacteristic(chara)) {
                this.onFail(-1, "gatt.writeCharacteristic() return false");
            }
        } catch (Throwable tr) {
            Log.e(TAG, "writeCharacteristic", tr);
            this.onFail(-1, tr.getMessage());
        }
    }

    public void readCharacteristic(UUID serviceUUID, UUID uuid) {
        try {
            if (null == gatt) {
                Log.e(TAG, "connect to miband first");
                throw new Exception("connect to miband first");
            }
            BluetoothGattCharacteristic chara = gatt.getService(serviceUUID).getCharacteristic(uuid);
            if (null == chara) {
                this.onFail(-1, "BluetoothGattCharacteristic " + uuid + " is not exsit");
                return;
            }
            if (false == this.gatt.readCharacteristic(chara)) {
                this.onFail(-1, "gatt.readCharacteristic() return false");
            }
        } catch (Throwable tr) {
            Log.e(TAG, "readCharacteristic", tr);
            this.onFail(-1, tr.getMessage());
        }
    }

    public void readCharacteristic(UUID uuid) {
        this.readCharacteristic(Profile.UUID_SERVICE_MILI, uuid);
    }

    public void readRssi() {
        try {
            if (null == gatt) {
                Log.e(TAG, "connect to miband first");
                throw new Exception("connect to miband first");
            }
            this.gatt.readRemoteRssi();
        } catch (Throwable tr) {
            Log.e(TAG, "readRssi", tr);
            this.onFail(-1, tr.getMessage());
        }

    }

    public boolean containListener(UUID characteristicId) {
       return notifyListeners.containsKey(characteristicId);
    }
    public void setNotifyListener(UUID serviceUUID, UUID characteristicId, NotifyListener listener) {
        if (null == gatt) {
            Log.e(TAG, "connect to miband first");
            return;
        }

        BluetoothGattCharacteristic chara = gatt.getService(serviceUUID).getCharacteristic(characteristicId);
        if (chara == null) {
            Log.e(TAG, "characteristicId " + characteristicId.toString() + " not found in service " + serviceUUID.toString());
            return;
        }


        this.gatt.setCharacteristicNotification(chara, true);
        BluetoothGattDescriptor descriptor = chara.getDescriptor(Profile.UUID_DESCRIPTOR_UPDATE_NOTIFICATION);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        this.gatt.writeDescriptor(descriptor);
        this.notifyListeners.put(characteristicId, listener);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            gatt.close();
            if (this.disconnectedListener != null)
                this.disconnectedListener.onNotify(null);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        String action = "reading characteristic " +Profile.nameMap.get(characteristic.getUuid());
        if (BluetoothGatt.GATT_SUCCESS == status) {
            this.onSuccess(action);
        } else {
            this.onFail(status, action);
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        String action = "writing characteristic " +Profile.nameMap.get(characteristic.getUuid());
        if (BluetoothGatt.GATT_SUCCESS == status) {
            this.onSuccess(action);
        } else {
            this.onFail(status, action);
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        String action = "read remote Rssi " + String.valueOf(rssi);
        if (BluetoothGatt.GATT_SUCCESS == status) {
            this.onSuccess(action);
        } else {
            this.onFail(status, "onCharacteristicRead fail");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.gatt = gatt;
            this.onSuccess(null);
        } else {
            this.onFail(status, "onServicesDiscovered fail");
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        if (this.notifyListeners.containsKey(characteristic.getUuid())) {
            this.notifyListeners.get(characteristic.getUuid()).onNotify(characteristic.getValue());
        }
    }

    String Tag = "BluetoothIO";
    private void onSuccess(String action) {
        Log.i(Tag, String.format("success when %s", action));
    }

    private void onFail(int errorCode, String action) {
        Log.i(Tag, String.format("Error found when %s, Error Code %d", action, errorCode));
    }

}
