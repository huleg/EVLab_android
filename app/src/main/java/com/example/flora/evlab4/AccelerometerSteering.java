package com.example.flora.evlab4;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.UUID;

import static java.lang.Math.acos;
import static java.lang.Math.asin;

public class AccelerometerSteering extends Activity implements SensorEventListener {

    //Bluetooth settings
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;

    //Send data via Bluetooth
    String steeringAc = String.valueOf(0);
    String throttleAc = String.valueOf(0);
    String brakeAc = String.valueOf(0);
    String send = String.valueOf(0);
    String checksum = String.valueOf(0);
    int checksum_int = 0;

    //SPP UUID
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float x = 0;
    private float y = 0;

    private int steering = 0;
    private int throttle = 0;
    private int brake = 0;

    double convert = 180 / Math.PI;

    public boolean forward = true;
    public boolean motorOn = false;

    public float angleZ_X, angleZ_Y, angleZCalc;

    final static int middlePoint = 127;
    final static float rangeN = 127;
    final static float rangeP = 128;
    final static float rangeSteering = 90;

    Button btn_changeDirection, btn_motor, btn_disconnect;

    private TextView angleZ, steeringValue, throttleValue, brakeValue;

    ImageView logo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accelerometer_steering);
        initializeViews();
        initializeButtons();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        steeringAc = String.valueOf(0);
        throttleAc = String.valueOf(0);
        brakeAc = String.valueOf(0);

    }

    public void initializeViews() {
        angleZ = (TextView) findViewById(R.id.angleZ);
        steeringValue = (TextView) findViewById(R.id.steering);
        throttleValue = (TextView) findViewById(R.id.throttle);
        brakeValue = (TextView) findViewById(R.id.brake);

        logo = (ImageView) findViewById(R.id.makerbay_logo);
    }

    public void initializeButtons() {
        btn_changeDirection = (Button) findViewById(R.id.btn_changeDirection);
        btn_changeDirection.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                forward = !forward;
                if (forward) {
                    btn_changeDirection.setText("Going forward - press to go backward");
                } else btn_changeDirection.setText("Going backward - press to go forward");
            }
        });
        btn_motor = (Button) findViewById(R.id.btn_motor);
        btn_motor.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                motorOn = !motorOn;
                if (motorOn) {
                    btn_motor.setText("Motor is ON");
                } else btn_motor.setText("Motor is OFF");
            }
        });
        btn_disconnect = (Button) findViewById(R.id.btn_disconnect);
        btn_disconnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                disconnect();
            }
        });
    }

    //onResume() register the accelerometer for listening the events
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        x = event.values[0];//always configure it according to device, the following code is tailored to a landscape tablet
        y = event.values[1];//x-axis along long side (positive direction is left <-- right), y-axis along short side (90° CCW to x)
        //z-axis through display plane (positive direction is out of display)

        ///call methods only when certain threshold in change is reached to save computing power?

        calcAngleZ_XY();
        calcSteering();

        displayValues();
        }


    void calcAngleZ_XY() { //calculate rotation around z-axis, based on gravitation along x and y
        double xBaseP = 10.25;
        double xBaseN = 9.35;
        double yBaseP = 9.65;

        double xPrec1 = 3.5;
        double xPrec2 = -3.8;
        double yPrec1 = 3.2;
        double yPrec2 = 3.5;

        if (x > 0) {
            if (x < xPrec1) {
                angleZCalc = (float) (asin(x / xBaseP) * convert);
            } else if (y < yPrec1) {
                angleZCalc = (float) (acos(y / yBaseP) * convert);
            } else
                angleZCalc = (float) convert * (float) ((asin(x / xBaseP) + (acos(y / yBaseP))) / 2);
        } else if (x < 0) {
            if (x > xPrec2) {
                angleZCalc = (float) (asin(x / xBaseN) * convert);
            } else if (y < yPrec2) {
                angleZCalc = -(float) (acos(y / yBaseP) * convert);
            } else
                angleZCalc = (float) convert * (float) ((asin(x / xBaseP) - (acos(y / yBaseP))) / 2);
        } else
            angleZCalc = 0;
    }

    void calcSteering() {
        if (angleZCalc > 0) {
            int steeringLeft = Math.round(angleZCalc * rangeP / rangeSteering);
            if (steeringLeft > rangeN) {
                steering = 0;
            } else steering = middlePoint - steeringLeft;
        } else if (angleZCalc < 0) {
            int steeringRight = -Math.round(angleZCalc * rangeN / rangeSteering);
            if (steeringRight > rangeP) {
                steering = 255;
            } else steering = middlePoint + steeringRight;
        } else steering = middlePoint;
    }

    void changeDirection() {
        forward = !forward;
        if (forward) {
            ((Button) findViewById(R.id.btn_changeDirection)).setText("Direction: forward");
        } else ((Button) findViewById(R.id.btn_changeDirection)).setText("Direction: backward");
    }

    void displayValues() {
        angleZ.setText(String.valueOf(angleZCalc));
        steeringValue.setText(String.valueOf(steering));
        throttleValue.setText(String.valueOf(throttle));
        brakeValue.setText(String.valueOf(brake));
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }


    //Send data via Bluetooth
    private void addValues() {
        if (btSocket!=null) {

            steeringAc = Float.toString(steering);
            throttleAc = Float.toString(throttle);
            brakeAc = Float.toString(brake);

            checksum_int = Integer.parseInt(steeringAc)+Integer.parseInt(throttleAc)+Integer.parseInt(brakeAc);
            checksum = String.valueOf(checksum);

            send = steeringAc+","+throttleAc+","+brakeAc+","+checksum+"#";


            try {
                btSocket.getOutputStream().write(send.toString().getBytes());
                btSocket.getOutputStream().flush();
            } catch (IOException e) {
                msg("Error");
            }
        }
    }


    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;
        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(AccelerometerSteering.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }
        @Override
        //while the progress dialog is shown, the connection is done in background
        protected Void doInBackground(Void... devices) {
            try {

                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }

            catch (IOException e) {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }

            return null;
        }
        @Override
        //after the doInBackground, it checks if everything went fine
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }

            else {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

    private void disconnect() {
        if (btSocket!=null) {
            try {
                btSocket.close();
            }
            catch (IOException e)
            { msg("Error Disconnect");}
        }
        finish();
    }


}
