package com.example.tello_neurosky;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.EEGPower;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Collections;

import jwave.Transform;
import jwave.transforms.FastWaveletTransform;
import jwave.transforms.wavelets.daubechies.Daubechies2;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    TelloDrone telloDrone;

    private Button btn_start = null;
    private Button btn_stop = null;
    private Switch switchDrone = null;
    private GraphView graph1 = null;
    private GraphView graph2 = null;
    private GraphView graph3 = null;

    private BluetoothAdapter mBluetoothAdapter = null;
    private TgStreamReader tgStreamReader = null;
    private int badPacketCount = 0;

    private int rawDataLength = 768; // A row eeg data from NeuroSky headset
    private int timeAnalysisLength = 512; // The data for wavelet forward transform

    private int attentionAndMeditationDataLength = 100;

    private ArrayList<Integer> rawData = new ArrayList<Integer>(Collections.nCopies(rawDataLength, 0)); // 512 Hz - 3 seconds 1536
    private ArrayList<Integer> attentionData = new ArrayList<Integer>(Collections.nCopies(attentionAndMeditationDataLength, 0));
    private ArrayList<Integer> meditationData = new ArrayList<Integer>(Collections.nCopies(attentionAndMeditationDataLength, 0));

    private LineGraphSeries<DataPoint> series1 = null;
    private LineGraphSeries<DataPoint> series2 = null;

    private LineGraphSeries<DataPoint> attentionSeries = null;
    private LineGraphSeries<DataPoint> meditationSeries = null;

    private DataPoint[] dataPoints1;
    private DataPoint[] dataPoints2;

    private DataPoint[] attentionPoints;
    private DataPoint[] meditationPoints;


    //Fast Wavelet transform class from graetz23 https://github.com/graetz23/JWave
    //But before data
    double[ ] arrTime = new double[timeAnalysisLength];
    double[ ] arrHilb = new double[timeAnalysisLength];
    Transform t = new Transform( new FastWaveletTransform( new Daubechies2( ) ) );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // The Tello drone
        telloDrone = new TelloDrone();
        initView();

        try {
            // Make sure that the device supports Bluetooth and Bluetooth is on
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                finish();
				//return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
            return;
        }

        // Constructor public TgStreamReader(BluetoothAdapter ba, TgStreamHandler tgStreamHandler)
        tgStreamReader = new TgStreamReader(mBluetoothAdapter, callbackFunc);
        // setGetDataTimeOutTime, the default time is 5s, please call it before connect() of connectAndStart()
        tgStreamReader.setGetDataTimeOutTime(6);
        // startLog, you will get more sdk log by logcat if you call this function
        tgStreamReader.startLog();

    }

    private void initView() {

        btn_start = (Button) findViewById(R.id.btn_start);
        btn_stop = (Button) findViewById(R.id.btn_stop);
        switchDrone = (Switch) findViewById(R.id.switchDrone);

        graph1 = (GraphView) findViewById(R.id.graph1);
        graph2 = (GraphView) findViewById(R.id.graph2);
        graph3 = (GraphView) findViewById(R.id.graph3);

        dataPoints1 = new DataPoint[rawData.size()];
        dataPoints2 = new DataPoint[rawData.size()];

        attentionPoints = new DataPoint[attentionData.size()];
        meditationPoints = new DataPoint[meditationData.size()];

        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Log.d("iBlink", "onClick Start!!!");
                badPacketCount = 0;

                // isBTConnected
                if(tgStreamReader != null && tgStreamReader.isBTConnected()){

                    // Prepare for connecting
                    tgStreamReader.stop();
                    tgStreamReader.close();
                }

                // Using connect() and start() to replace connectAndStart(),
                // please call start() when the state is changed to STATE_CONNECTED
                tgStreamReader.connect();
                //tgStreamReader.connectAndStart();
            }
        });

        btn_stop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View arg0){
                tgStreamReader.stop();
                tgStreamReader.close();
            }
        });

        switchDrone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if(isChecked) {
                    telloDrone.ready = true;
                }
                else {
                    telloDrone.ready = false;
                }
            }
        });

        // activate horizontal scrolling
        //graph1.getViewport().setScrollable(true);

        graph1.getViewport().setYAxisBoundsManual(true);
        graph1.getViewport().setMinY(-rawDataLength);
        graph1.getViewport().setMaxY(rawDataLength);
        // set manual X bounds
        graph1.getViewport().setXAxisBoundsManual(true);
        graph1.getViewport().setMinX(0);
        graph1.getViewport().setMaxX(rawDataLength);

        for (int i = 0; i < rawData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints1[i] = new DataPoint(i, rawData.get(i));
        }

        series1 = new LineGraphSeries<DataPoint>(dataPoints1);
        series1.setTitle("Fpz EEG data");
        series1.setColor(Color.GREEN);

        graph1.addSeries(series1);

        //graph2.getViewport().setScrollable(true);
        graph2.getViewport().setYAxisBoundsManual(true);
        graph2.getViewport().setMinY(-timeAnalysisLength);
        graph2.getViewport().setMaxY(timeAnalysisLength);
        // set manual X bounds
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setMinX(15);
        graph2.getViewport().setMaxX(30);

        for (int i = 0; i < rawData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints2[i] = new DataPoint(i, rawData.get(i));
        }
        series2 = new LineGraphSeries<DataPoint>(dataPoints2);
        series2.setTitle("Wavelet analysis");
        series2.setColor(Color.GREEN);
        graph2.addSeries(series2);


        //graph3.getViewport().setScrollable(true);
        graph3.getViewport().setYAxisBoundsManual(true);
        graph3.getViewport().setMinY(0);
        graph3.getViewport().setMaxY(attentionAndMeditationDataLength);
        // set manual X bounds
        graph3.getViewport().setXAxisBoundsManual(true);
        graph3.getViewport().setMinX(0);
        graph3.getViewport().setMaxX(attentionAndMeditationDataLength);

        for (int i = 0; i < attentionData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            attentionPoints[i] = new DataPoint(i, attentionData.get(i));
            meditationPoints[i] = new DataPoint(i, meditationData.get(i));
        }

        attentionSeries = new LineGraphSeries<DataPoint>(attentionPoints);
        attentionSeries.setTitle("Attention");
        attentionSeries.setColor(Color.RED);

        meditationSeries = new LineGraphSeries<DataPoint>(meditationPoints);
        meditationSeries.setTitle("Meditation");
        meditationSeries.setColor(Color.BLUE);

        graph3.addSeries(attentionSeries);
        graph3.addSeries(meditationSeries);


    }

    // TgStreamHandler
    private final TgStreamHandler callbackFunc = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //Recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    tgStreamReader.startRecordRawData();

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    tgStreamReader.stopRecordRawData();

                    showToast("Get data time out!", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.

                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    break;
            }

            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_STATE;
            msg.arg1 = connectionStates;
            LinkDetectedHandler.sendMessage(msg);

        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG,"onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
            badPacketCount ++;
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_BAD_PACKET;
            msg.arg1 = badPacketCount;
            LinkDetectedHandler.sendMessage(msg);
        }


        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.

            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = datatype;
            msg.arg1 = data;
            msg.obj = obj;
            LinkDetectedHandler.sendMessage(msg);

            //Log.i(TAG,"onDataReceived");
        }
    };

    //private boolean isPressing = false;
    private static final int MSG_UPDATE_BAD_PACKET = 1001;
    private static final int MSG_UPDATE_STATE = 1002;

    @SuppressLint("HandlerLeak")
    private final Handler LinkDetectedHandler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            // (8) demo of MindDataType
            switch (msg.what) {
                case MindDataType.CODE_RAW:
                    proccessDataWave(msg.arg1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    //Log.d(TAG, "HeadDataType.CODE_MEDITATION " + msg.arg1);
                    //tv_meditation.setText("" +msg.arg1 );
                    proccessMeditationData(msg.arg1);
                    break;
                case MindDataType.CODE_ATTENTION:
                    //Log.d(TAG, "CODE_ATTENTION " + msg.arg1);
                    //tv_attention.setText("" +msg.arg1 );
                    proccessAttentionData(msg.arg1);
                    break;
                case MindDataType.CODE_EEGPOWER:
                    EEGPower power = (EEGPower)msg.obj;
                    if(power.isValidate()){
                        //tv_delta.setText("" +power.delta);
                        //tv_theta.setText("" +power.theta);
                        //tv_lowalpha.setText("" +power.lowAlpha);
                        //tv_highalpha.setText("" +power.highAlpha);
                        //tv_lowbeta.setText("" +power.lowBeta);
                        //tv_highbeta.setText("" +power.highBeta);
                        //tv_lowgamma.setText("" +power.lowGamma);
                        //tv_middlegamma.setText("" +power.middleGamma);
                    }
                    break;
                case MindDataType.CODE_POOR_SIGNAL://
                    //int poorSignal = msg.arg1;
                    //Log.d(TAG, "poorSignal:" + poorSignal);
                    //tv_ps.setText(""+msg.arg1);

                    break;
                case MSG_UPDATE_BAD_PACKET:
                    Log.d(TAG, "BAD PACKET: " + msg.arg1);

                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public void showToast(final String msg,final int timeStyle){
        MainActivity.this.runOnUiThread(new Runnable()
        {
            public void run()
            {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    //Blink detection alg
    private double maxThreshold = 400;
    private double minThreshold = 0;

    private int numbersOfSubProccessing = 0;
    private int numberOfBlinks = 0;
    private int TwoBlinks = 0;
    private int ThreeBlinks = 0;

    public void proccessDataWave(int data) {
        rawData.remove(0);
        rawData.add(data);

        for (int i = 0; i < rawData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints1[i] = new DataPoint(i, rawData.get(i));

            if(i < timeAnalysisLength) arrTime[i] = Double.valueOf(rawData.get(i));
            //dataPoints2[i] = new DataPoint(i, rawData.get(i));
        }
        series1.resetData(dataPoints1);

        arrHilb = t.forward(arrTime); // 1-D FWT Haar forward
        for (int i = 0; i < arrHilb.length; ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints2[i] = new DataPoint(i, arrHilb[i]);
        }
        series2.resetData(dataPoints2);

        if(numbersOfSubProccessing < 15) {
            for(int i = 15; i<40; ++i){
                //if ( arrHilb[i] < minThreshold/4) {
                    if(arrHilb[i] > maxThreshold) {
                        if(arrHilb[i+1] < minThreshold) {
                            //if(arrHilb[i+3] > minThreshold/2) {
                                ++numberOfBlinks;
                                //Log.d(TAG, String.valueOf(numberOfBlinks));
                                //i+=3;
                            //}
                        }
                    }
                //}
            }

            if(numberOfBlinks == 2){
                TwoBlinks = numberOfBlinks;
                if(ThreeBlinks != 3) ++numbersOfSubProccessing;
            }

            if(numberOfBlinks == 3){
                ThreeBlinks = numberOfBlinks;
                ++numbersOfSubProccessing;
            }

            numberOfBlinks = 0;
        }


        //if(numberOfBlinks == 3) Log.d(TAG, "Three blinks was detected ");

        if(numbersOfSubProccessing > arrHilb.length * 4) {
            Log.d(TAG, String.valueOf(numbersOfSubProccessing));

            if(TwoBlinks == 2 && ThreeBlinks != 3) {
                Log.d(TAG, "Two blinks was detected");
                showToast("Two blinks was detected", Toast.LENGTH_SHORT);

                telloDrone.setCommand("command");
                telloDrone.setCommand("takeoff");
            }

            if(ThreeBlinks == 3) {
                Log.d(TAG, "Three blinks was detected ");
                showToast("Three blinks was detected", Toast.LENGTH_SHORT);

                if (telloDrone.isUp) {
                    telloDrone.setCommand("command");
                    telloDrone.setCommand("land");
                }
            }

            TwoBlinks = 0;
            ThreeBlinks = 0;
            numbersOfSubProccessing = 0;
        }

        if(numbersOfSubProccessing >= 15)  {
            ++numbersOfSubProccessing;
        }

        //++numbersOfSubProccessing;
    }

    public void proccessAttentionData(int attention) {

        attentionData.remove(0);
        attentionData.add(attention);

        for (int i = 0; i < attentionData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            attentionPoints[i] = new DataPoint(i, attentionData.get(i));
        }

        attentionSeries.resetData(attentionPoints);
    }

    public void proccessMeditationData(int meditation) {

        meditationData.remove(0);
        meditationData.add(meditation);

        for (int i = 0; i < attentionData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            meditationPoints[i] = new DataPoint(i, meditationData.get(i));
        }

        meditationSeries.resetData(meditationPoints);
    }

}