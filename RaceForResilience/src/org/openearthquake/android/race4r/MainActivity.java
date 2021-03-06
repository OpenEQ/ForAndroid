package org.openearthquake.android.race4r;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.openearthquake.android.race4r.net.HttpPostAsyncTask;
import org.openearthquake.android.race4r.net.ServerManager;
import org.openearthquake.android.race4r.util.RingBuffer;
import org.openearthquake.android.race4r.util.StorageManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class MainActivity extends Activity implements LocationListener, Runnable{
    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "";      // SENDER_ID here

    public static final int BUF_SIZE = 100;
    public static final int DATABYTE_SIZE = 12;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    static final String TAG = "race4r_main";
    private Context context = null;
    
    private RingBuffer mRingBuffer;
    private LocationManager mLocationManager;
    private Location mLastLocation = new Location("");
    private GoogleCloudMessaging gcm = null;
    private static String myUuid = null;
    private static String regId = null;
    private ServerManager mgrServer = null;
    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;
    
    // Views
    private TextView mDisplay;
    private TextView mTextLat;
    private TextView mTextLng;
    private TextView mTextLastUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.context = getApplicationContext();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        this.mRingBuffer = new RingBuffer(BUF_SIZE, DATABYTE_SIZE);
        this.mLocationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        this.mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        this.mgrServer = new ServerManager();

        // Views
        mDisplay = (TextView) findViewById(R.id.textViewDisplay);
        mTextLat = (TextView) findViewById(R.id.textViewLatitude);
        mTextLng = (TextView) findViewById(R.id.textViewLongitude);
        mTextLastUpdate = (TextView) findViewById(R.id.textViewLastUpdate);

        myUuid = StorageManager.getUuid(context);

        // 最適なプロバイダーを取り出す
        Criteria criteria = new Criteria();
        criteria.setSpeedRequired(false);
        criteria.setBearingRequired(false);
        criteria.setAltitudeRequired(false);

        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(context);
            regId = StorageManager.getRegistrationId(context);
            Log.d(TAG, "regId=" + regId);
            if (regId.isEmpty()) {
                Log.i(TAG, "invoke registerInBackground()...");
                registerInBackground();
            } else {
                Log.i(TAG, "No valid Google Play Services APK found.");
            }
        }
    }

    public void onClick(final View v) {
        switch(v.getId()) {
            case R.id.buttonRegister:
                this.registerInBackground();
                break;
            case R.id.buttonUnreg:
                new HttpPostAsyncTask(this).execute("unreg", myUuid);
                break;
            case R.id.buttonSend:
                break;
        }
    }
    @Override
    public void onLocationChanged(Location location) {
        this.mLastLocation.set(location);
        // 座標とかを表示する
        this.mTextLat.setText(location.getLatitude() + "");
        this.mTextLng.setText(location.getLongitude() + "");
        this.mTextLastUpdate.setText(SimpleDateFormat.getInstance().format(location.getTime()));
        // this.mEditTextStatus.setText(location.getProvider());
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void onResume() {
        if (this.mLocationManager != null) {
            this.checkGpsCondition();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (this.mLocationManager != null) {
            this.mLocationManager.removeUpdates(this);
        }
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    private void openAccessory(UsbAccessory accessory) {
        // アクセサリにアクセスするためのファイルディスクリプタを取得
        
        Log.d(TAG,"starting openAccessory()");
        
        mFileDescriptor = mUsbManager.openAccessory(accessory);

        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();

            // 入出力用のストリームを確保
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);

            // この中でアクセサリとやりとりする
            Log.d(TAG, "Kicking thread");
            Thread thread = new Thread(null, this, "DemoKit");
            thread.start();
            Log.d(TAG, "accessory opened");

            //enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }
    
    private void checkGpsCondition() {
        // 利用可能なプロバイダを全部使う(GPSがだめならネットワークみたいな考え方)
        // GPSは空が見えるところでないととれにくい。その場合はネットワークしか座標が取り出せない
        List<String> providerList = this.mLocationManager.getProviders(true);
         
        // ////////////////////////////////////////////////////////////
        // プロバイダリストの表示
        String providerNames = "";
        for (int i = 0; i < providerList.size(); i++) {
            providerNames = providerNames + " " + providerList.get(i);
        }
        this.mDisplay.setText(providerNames);
         
        // GPSが使えない 
        if (this.mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false){
            new AlertDialog.Builder(this)
                .setTitle("GPSが無効")
                .setMessage("GPS機能が有効ではありません。\n\n有効にすることで現在位置をさらに正確に検出できるようになります")
                .setPositiveButton("設定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            startActivity(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"));
                        } catch (final ActivityNotFoundException e) {};
                    }
                })
                .setNegativeButton("しない", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .create()
                .show();
        }
    
        // 全部の機能を使って座標を取り出す
        for (int i = 0; i < providerList.size(); i++) {
            this.mLocationManager.requestLocationUpdates(
                    providerList.get(i), (long) 10000, (float) 1, this);
        }
    }
    
    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
    
    /**
     * Registers the application with GCM servers asynchronously.
     *
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regId = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regId;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    // TODO:GPS Info
                    mgrServer.sendRegistrationIdToBackend(context, regId, myUuid, mLastLocation.getLatitude(), mLastLocation.getLongitude());

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    StorageManager.storeRegistrationId(context, regId);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }


    @Override
    public void run() {
        // TODO Auto-generated method stub
        
    }
}
