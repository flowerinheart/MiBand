package com.martindisch.accelerometer;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.martindisch.accelerometer.Model.Profile;
import com.martindisch.accelerometer.Model.Protocol;

import java.util.Arrays;

public class MiBand {

    private static final String TAG = "miband-android";

    private Context context;
    BluetoothIO io;

    public MiBand(Context context) {
        this.context = context;
        this.io = new BluetoothIO();
    }
    /**
     * 连接指定的手环
     *
     */
    public void connect(BluetoothDevice device) {
        this.io.connect(context, device);
    }

    public void setDisconnectedListener(BluetoothIO.NotifyListener disconnectedListener) {
        this.io.setDisconnectedListener(disconnectedListener);
    }

    public BluetoothDevice getDevice() {
        return this.io.getDevice();
    }

    /**
     * 读取和连接设备的信号强度RSSI值
     *
     */
    public void readRssi() {
        this.io.readRssi();
    }



    private static double[][] handleSensorData(byte[] value) {
        String Tag = "MiBand.handleSensorData";
        int counter = 0, step = 0;
        double xAxis = 0.0, yAxis = 0.0, zAxis = 0.0;
        double scale_factor = 1000.0;
        double gravity = 9.81;

        if ((value.length - 2) % 6 != 0) {
            Log.i(Tag, "GOT UNEXPECTED SENSOR DATA WITH LENGTH: " + value.length);
            for (byte b : value) {
                Log.i(Tag, "DATA: " + String.format("0x%4x", b));
            }
            return null;
        } else {
            int len = (value.length - 2) / 6;
            double[][] res = new double[len][];
            counter = (value[0] & 0xff) | ((value[1] & 0xff) << 8);
            for (int idx = 0; idx < ((value.length - 2) / 6); idx++) {
                step = idx * 6;

                // Analyse X-axis data
                int xAxisRawValue = (value[step + 2] & 0xff) | ((value[step + 3] & 0xff) << 8);
                int xAxisSign = (value[step + 3] & 0x30) >> 4;
                int xAxisType = (value[step + 3] & 0xc0) >> 6;
                if (xAxisSign == 0) {
                    xAxis = xAxisRawValue & 0xfff;
                } else {
                    xAxis = (xAxisRawValue & 0xfff) - 4097;
                }
                xAxis = (xAxis * 1.0 / scale_factor) * gravity;

                // Analyse Y-axis data
                int yAxisRawValue = (value[step + 4] & 0xff) | ((value[step + 5] & 0xff) << 8);
                int yAxisSign = (value[step + 5] & 0x30) >> 4;
                int yAxisType = (value[step + 5] & 0xc0) >> 6;
                if (yAxisSign == 0) {
                    yAxis = yAxisRawValue & 0xfff;
                } else {
                    yAxis = (yAxisRawValue & 0xfff) - 4097;
                }
                yAxis = (yAxis / scale_factor) * gravity;

                // Analyse Z-axis data
                int zAxisRawValue = (value[step + 6] & 0xff) | ((value[step + 7] & 0xff) << 8);
                int zAxisSign = (value[step + 7] & 0x30) >> 4;
                int zAxisType = (value[step + 7] & 0xc0) >> 6;
                if (zAxisSign == 0) {
                    zAxis = zAxisRawValue & 0xfff;
                } else {
                    zAxis = (zAxisRawValue & 0xfff) - 4097;
                }
                zAxis = (zAxis / scale_factor) * gravity;

                // Print results in log
                Log.i(Tag, "READ SENSOR DATA VALUES: counter:" + counter + " step:" + step + " x-axis:" + String.format("%.03f", xAxis) + " y-axis:" + String.format("%.03f", yAxis) + " z-axis:" + String.format("%.03f", zAxis) + ";");
                double[] temp = {xAxis, yAxis, zAxis};
                res[idx] = temp;
            }
            return res;
        }
    }
    /**
     * 重力感应器数据通知监听, 设置完之后需要另外使用 {@link MiBand#enableRealtimeStepsNotify} 开启 和
     * {@link MiBand##disableRealtimeStepsNotify} 关闭通知
     *
     * @param listener
     */
    public void setSensorDataNotifyListener(final BluetoothIO.RealtimeSensorNotifyListener listener) {
        if(io.containListener(Profile.UUID_CHAR_SENSOR_DATA))
            return;
        this.io.setNotifyListener(Profile.UUID_SERVICE_MILI, Profile.UUID_CHAR_SENSOR_DATA, new BluetoothIO.NotifyListener() {
            @Override
            public void onNotify(byte[] data) {
                double[][] axis = handleSensorData(data);
                listener.onNotify(axis);
            }
        });
    }



    /**
     * 开启重力感应器数据通知
     */
    public void enableSensorDataNotify() {
        this.io.writeCharacteristic(Profile.UUID_CHAR_CONTROL_POINT, Protocol.ENABLE_SENSOR_DATA_NOTIFY);
    }

    /**
     * 关闭重力感应器数据通知
     */
    public void disableSensorDataNotify() {
        this.io.writeCharacteristic(Profile.UUID_CHAR_CONTROL_POINT, Protocol.DISABLE_SENSOR_DATA_NOTIFY);
    }

    /**
     * 实时步数通知监听器, 设置完之后需要另外使用 {@link MiBand#enableRealtimeStepsNotify} 开启 和
     * {@link MiBand##disableRealtimeStepsNotify} 关闭通知
     *
     * @param listener
     */
    public void setRealtimeStepsNotifyListener(final BluetoothIO.RealtimeStepsNotifyListener listener) {
        this.io.setNotifyListener(Profile.UUID_SERVICE_MILI, Profile.UUID_CHAR_REALTIME_STEPS, new BluetoothIO.NotifyListener() {

            @Override
            public void onNotify(byte[] data) {
                Log.d(TAG, Arrays.toString(data));
                if (data.length == 4) {
                    int steps = data[3] << 24 | (data[2] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | (data[0] & 0xFF);
                    listener.onNotify(steps);
                }
            }
        });
    }

    /**
     * 开启实时步数通知
     */
    public void enableRealtimeStepsNotify() {
        this.io.writeCharacteristic(Profile.UUID_CHAR_CONTROL_POINT, Protocol.ENABLE_REALTIME_STEPS_NOTIFY);
    }

    /**
     * 关闭实时步数通知
     */
    public void disableRealtimeStepsNotify() {
        this.io.writeCharacteristic(Profile.UUID_CHAR_CONTROL_POINT, Protocol.DISABLE_REALTIME_STEPS_NOTIFY);
    }


    public void showServicesAndCharacteristics() {
        for (BluetoothGattService service : io.gatt.getServices()) {
            Log.d(TAG, "onServicesDiscovered:" + service.getUuid());

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                Log.d(TAG, "  char:" + characteristic.getUuid());

                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                    Log.d(TAG, "    descriptor:" + descriptor.getUuid());
                }
            }
        }
    }
}
