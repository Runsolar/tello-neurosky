package com.example.tello_neurosky;

import androidx.annotation.NonNull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;

import jwave.Transform;
import jwave.transforms.FastWaveletTransform;
import jwave.transforms.wavelets.daubechies.Daubechies2;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    TelloDrone telloDrone;

    private Button btn_start = null;
    private Button btn_stop = null;
    private Button btn_set_blink_thresholds = null;
    private Button btn_set_bactivity_thresholds = null;

    private ToggleButton switchDrone = null;
    private GraphView graph1 = null;
    private GraphView graph2 = null;
    private GraphView graph3 = null;

    private double maxBlinkThreshold = 1000;
    private double minBlinkThreshold = -500;
    private EditText edTxtMaxBlinkThreshold = null;
    private EditText edTxtMinBlinkThreshold = null;

    private int attentionAndMeditationDataLength = 100;
    private int currentAttention = 0;
    private int currentMeditation = 0;

    private int maxAtteThreshold = 70;
    private int minAtteThreshold = 50;
    private int maxMedThreshold = 70;
    private int minMedThreshold = 50;

    private EditText edTextMaxAtteThreshold = null;
    private EditText edTextMinAtteThreshold = null;
    private EditText edTextMaxMedThreshold = null;
    private EditText edTextMinMedThreshold = null;

    private BluetoothAdapter mBluetoothAdapter = null;
    private TgStreamReader tgStreamReader = null;
    private int badPacketCount = 0;

    private int rawDataLength = 1024; // A row eeg data from NeuroSky headset
    private int timeAnalysisLength = 1024; // The data for wavelet forward transform
    private int blinksSeriesBeginningEdge = 15;
    private int blinksSeriesEndingEdge = 30;

    private int blinksArrHilbLenght = blinksSeriesEndingEdge - blinksSeriesBeginningEdge;

    private ArrayList<Integer> rawData = new ArrayList<Integer>(Collections.nCopies(rawDataLength, 0)); // 512 Hz - 3 seconds 1536
    private ArrayList<Integer> attentionData = new ArrayList<Integer>(Collections.nCopies(attentionAndMeditationDataLength, 0));
    private ArrayList<Integer> meditationData = new ArrayList<Integer>(Collections.nCopies(attentionAndMeditationDataLength, 0));

    private LineGraphSeries<DataPoint> series1 = null;
    private LineGraphSeries<DataPoint> series2 = null;

    private LineGraphSeries<DataPoint> maxBlinkThresholdSeries = null;
    private LineGraphSeries<DataPoint> minBlinkThresholdSeries = null;
    private Paint paintForBlinkThresholdSeries = null;

    private LineGraphSeries<DataPoint> attentionSeries = null;
    private LineGraphSeries<DataPoint> meditationSeries = null;

    private DataPoint[] dataPoints1;
    private DataPoint[] dataPoints2;

    private DataPoint[] maxBlinkThresholdPoints;
    private DataPoint[] minBlinkThresholdPoints;

    private DataPoint[] attentionPoints;
    private DataPoint[] meditationPoints;


    //Fast Wavelet transform class from graetz23 https://github.com/graetz23/JWave
    //But before data
    double[] arrTime = new double[timeAnalysisLength];
    double[] arrHilb = new double[timeAnalysisLength];
    double[] blinksArrHilb = new double[blinksArrHilbLenght];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.activity_main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.window_title);

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
        btn_set_blink_thresholds = (Button) findViewById(R.id.btn_set_blink_thresholds);
        edTxtMaxBlinkThreshold = (EditText) findViewById(R.id.edTxtMaxBlinkThreshold);
        edTxtMinBlinkThreshold = (EditText) findViewById(R.id.edTextMinBlinkThreshold);

        btn_set_bactivity_thresholds = (Button) findViewById(R.id.btn_set_bactivity_thresholds);
        edTextMaxAtteThreshold = (EditText) findViewById(R.id.edTextMaxAtteThreshold);
        edTextMinAtteThreshold = (EditText) findViewById(R.id.edTextMinAtteThreshold);
        edTextMaxMedThreshold = (EditText) findViewById(R.id.edTextMaxMedThreshold);
        edTextMinMedThreshold = (EditText) findViewById(R.id.edTextMinMedThreshold);

        switchDrone = (ToggleButton) findViewById(R.id.switchDrone);

        graph1 = (GraphView) findViewById(R.id.graph1);
        graph2 = (GraphView) findViewById(R.id.graph2);
        graph3 = (GraphView) findViewById(R.id.graph3);

        dataPoints1 = new DataPoint[rawData.size()];
        dataPoints2 = new DataPoint[blinksArrHilbLenght];

        maxBlinkThresholdPoints = new DataPoint[blinksArrHilbLenght];
        minBlinkThresholdPoints = new DataPoint[blinksArrHilbLenght];

        attentionPoints = new DataPoint[attentionData.size()];
        meditationPoints = new DataPoint[meditationData.size()];

        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Log.d("iBlink", "onClick Start!!!");
                badPacketCount = 0;

                // isBTConnected
                if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

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

        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                tgStreamReader.stop();
                tgStreamReader.close();
            }
        });

        btn_set_blink_thresholds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                maxBlinkThreshold = Double.valueOf(edTxtMaxBlinkThreshold.getText().toString());
                minBlinkThreshold = Double.valueOf(edTxtMinBlinkThreshold.getText().toString());

                for (int i = 0; i < blinksArrHilbLenght; ++i) {
                    maxBlinkThresholdPoints[i] = new DataPoint(i, maxBlinkThreshold);
                    minBlinkThresholdPoints[i] = new DataPoint(i, minBlinkThreshold);
                }

                maxBlinkThresholdSeries.resetData(maxBlinkThresholdPoints);
                minBlinkThresholdSeries.resetData(minBlinkThresholdPoints);

                showToast("New thresholds was set", Toast.LENGTH_SHORT);
            }
        });

        btn_set_bactivity_thresholds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                maxAtteThreshold = Integer.valueOf(edTextMaxAtteThreshold.getText().toString());
                minAtteThreshold = Integer.valueOf(edTextMinAtteThreshold.getText().toString());
                maxMedThreshold = Integer.valueOf(edTextMaxMedThreshold.getText().toString());
                minMedThreshold = Integer.valueOf(edTextMinMedThreshold.getText().toString());

                showToast("New thresholds was set", Toast.LENGTH_SHORT);
            }
        });

        switchDrone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if (isChecked) {
                    telloDrone.ready = true;
                    showToast("The drone goes ready", Toast.LENGTH_SHORT);
                } else {
                    if (telloDrone.isUp) {
                        telloDrone.setCommand("command");
                        telloDrone.setCommand("land");
                    }
                    telloDrone.ready = false;
                    showToast("The drone goes unready", Toast.LENGTH_SHORT);
                }
            }
        });

        // activate horizontal scrolling
        //graph1.getViewport().setScrollable(true);

        graph1.getViewport().setYAxisBoundsManual(true);
        graph1.getViewport().setMinY(-rawDataLength / 2);
        graph1.getViewport().setMaxY(rawDataLength / 2);
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
        graph2.getViewport().setMinX(0);
        graph2.getViewport().setMaxX(blinksArrHilbLenght);

        for (int i = 0; i < blinksArrHilbLenght; ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints2[i] = new DataPoint(i, 0);

            maxBlinkThresholdPoints[i] = new DataPoint(i, maxBlinkThreshold);
            minBlinkThresholdPoints[i] = new DataPoint(i, minBlinkThreshold);
        }
        series2 = new LineGraphSeries<DataPoint>(dataPoints2);
        series2.setTitle("Wavelet analysis");
        series2.setColor(Color.GREEN);
        graph2.addSeries(series2);


        maxBlinkThresholdSeries = new LineGraphSeries<DataPoint>(maxBlinkThresholdPoints);
        minBlinkThresholdSeries = new LineGraphSeries<DataPoint>(minBlinkThresholdPoints);
        //maxBlinkThresholdSeries.setColor(Color.RED);
        //minBlinkThresholdSeries.setColor(Color.BLUE);

        // custom paint to make a dotted line for Thresholds Series
        paintForBlinkThresholdSeries = new Paint();
        paintForBlinkThresholdSeries.setStyle(Paint.Style.STROKE);
        paintForBlinkThresholdSeries.setColor(Color.YELLOW);
        paintForBlinkThresholdSeries.setStrokeWidth(8);
        paintForBlinkThresholdSeries.setPathEffect(new DashPathEffect(new float[]{8, 5}, 0));
        maxBlinkThresholdSeries.setCustomPaint(paintForBlinkThresholdSeries);
        minBlinkThresholdSeries.setCustomPaint(paintForBlinkThresholdSeries);

        graph2.addSeries(maxBlinkThresholdSeries);
        graph2.addSeries(minBlinkThresholdSeries);

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

    // TgStreamHandler, see more and thx for 11A11 https://github.com/11A11/MindWaveMobile
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
            Log.e(TAG, "onRecordFail: " + flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
            badPacketCount++;
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
                    processDataWave(msg.arg1);
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
                    EEGPower power = (EEGPower) msg.obj;
                    if (power.isValidate()) {
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

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    //Blink detection alg
    private int numbersOfSubProcessing = 0;
    private int numberOfBlinks = 0;
    private int TwoBlinks = 0;
    private int ThreeBlinks = 0;

    public void processDataWave(int data) {
        rawData.remove(0);
        rawData.add(data);

        for (int i = 0; i < rawData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            dataPoints1[i] = new DataPoint(i, rawData.get(i));

            //if(i < timeAnalysisLength) arrTime[i] = Double.valueOf(rawData.get(i));
            arrTime[i] = Double.valueOf(rawData.get(i));
        }
        series1.resetData(dataPoints1);

        if (!calcWaveletBusy) {

            // Calling a thread fpr 1-D Fast Wavelet Transform of Daubechies2 forward
            new calcWavelet(arrTime);

            try {
                Thread.sleep(0);
            } catch (InterruptedException e) {
                //System.out.println("Main thread Interrupted");
            }
            //System.out.println("Main thread exiting.");

        } else {
            for (int i = 0; i < blinksArrHilbLenght; ++i) {
                // add new DataPoint object to the array for each of your list entries
                dataPoints2[i] = new DataPoint(i, arrHilb[i + blinksSeriesBeginningEdge]);
                blinksArrHilb[i] = arrHilb[i + blinksSeriesBeginningEdge];
            }
            series2.resetData(dataPoints2);

            if (numbersOfSubProcessing < blinksArrHilbLenght) {

                numberOfBlinks = 0;
                for (int i = 0; i < blinksArrHilbLenght - 3; ++i) {
                    if (blinksArrHilb[i] < minBlinkThreshold / 4) {
                        if (blinksArrHilb[i + 1] > maxBlinkThreshold) {
                            if (blinksArrHilb[i + 2] < minBlinkThreshold) {
                                //if (blinksArrHilb[i + 3] > minBlinkThreshold / 2) {
                                    ++numberOfBlinks;
                                    //Log.d(TAG, String.valueOf(numberOfBlinks));
                                    //i+=3;
                                //}
                            }
                        }
                    }
                }

                if (numberOfBlinks == 1) {
                    numbersOfSubProcessing = 0;
                }

                if (numberOfBlinks == 2) {
                    TwoBlinks = numberOfBlinks;
                    numbersOfSubProcessing = 0;
                }

                if (numberOfBlinks == 3) {
                    ThreeBlinks = numberOfBlinks;
                    TwoBlinks = 0;
                    numbersOfSubProcessing = 0;
                }

                ++numbersOfSubProcessing;
            } else {
                ++numbersOfSubProcessing;
            }

            //System.out.println(numbersOfSubProccessing + "        " + numberOfBlinks);

            if (numbersOfSubProcessing > 8 * blinksArrHilbLenght) {

                if (TwoBlinks == 2 && ThreeBlinks != 3) {
                    Log.d(TAG, "Two blinks was detected");
                    showToast("Two blinks was detected", Toast.LENGTH_SHORT);

                    if (telloDrone.ready && !telloDrone.isUp) {
                        telloDrone.setCommand("command");
                        telloDrone.setCommand("takeoff");
                    }
                }

                if (ThreeBlinks == 3) {

                    Log.d(TAG, "Three blinks was detected ");
                    showToast("Three blinks was detected", Toast.LENGTH_SHORT);

                    if (telloDrone.isUp) {
                        telloDrone.setCommand("command");
                        telloDrone.setCommand("land");
                    }
                }

                numbersOfSubProcessing = 0;
                TwoBlinks = 0;
                ThreeBlinks = 0;
            }
        }

    }

    public void proccessAttentionData(int attention) {

        attentionData.remove(0);
        attentionData.add(attention);

        for (int i = 0; i < attentionData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            attentionPoints[i] = new DataPoint(i, attentionData.get(i));
        }

        attentionSeries.resetData(attentionPoints);

        if (attention > maxAtteThreshold && telloDrone.isUp && currentMeditation < minMedThreshold) {
            telloDrone.setCommand("command");
            telloDrone.setCommand("forward 20");
        }
    }

    public void proccessMeditationData(int meditation) {

        meditationData.remove(0);
        meditationData.add(meditation);

        for (int i = 0; i < attentionData.size(); ++i) {
            // add new DataPoint object to the array for each of your list entries
            meditationPoints[i] = new DataPoint(i, meditationData.get(i));
        }

        meditationSeries.resetData(meditationPoints);

        if (meditation > maxMedThreshold && telloDrone.isUp && currentAttention < minAtteThreshold) {
            telloDrone.setCommand("command");
            telloDrone.setCommand("back 20");
        }
    }

    // The Daubechies 2 fast wavelet transform in another thread for better capacities
    // Seem more and thx for graetz23 https://github.com/graetz23/JWave
    private Boolean calcWaveletBusy = false;
    Transform wt = new Transform(new FastWaveletTransform(new Daubechies2()));

    class calcWavelet implements Runnable {
        String name;
        Thread t;

        calcWavelet(double[] arrTime) {
            calcWaveletBusy = true;
            name = Thread.currentThread().getName();
            t = new Thread(this, name);
            //System.out.println("New thread: " + t);
            t.start();
        }

        public void run() {
            try {
                arrHilb = wt.forward(arrTime);
                Thread.sleep(10);

            } catch (InterruptedException e) {
                //System.out.println(name + "Interrupted");
            }
            //System.out.println(name + " exiting.");
            calcWaveletBusy = false;
        }
    }

}