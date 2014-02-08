package com.kurimotokenichi.www.earth003;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import com.google.android.c2dm.C2DMessaging;

public class Earth003Activity extends Activity implements Runnable, OnClickListener{
    public static Handler mH;
 
    private static final String TAG = "earth003";
    private static final String ACTION_USB_PERMISSION = "com.kurimotokenichi.www.earth003.action.USB_PERMISSION";    
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;

    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    
    private Button recordButton,idleButton, writeButton,sendButton,settingButton,registerButton, unregisterButton;
    private TextView sysMessage;
    
    private int state;
    public static final int IDLE_STATE = 0;
    public static final int RECORD_STATE = 1;
    public static final int WRITE_STATE = 2;
    public static final int SEND_STATE = 3;
    public static final int SETTING_STATE = 4;

    public static final int BUF_SIZE = 100;
    public static final int DATABYTE_SIZE = 12;
    
    private static final int FTP_PORT = 21;     
    private static final String HOSTNAME = "";    // Hostname here
    private static final String FTPUSER = "";     // Username here
    private static final String FTPPASSWD = "";   // Password here

    public static int sending_flag = 0;
    public static int writing_flag = 0;
    
    private MyRingBuffer ringbuf;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Intent からアクセサリを取得
                    UsbAccessory accessory = UsbManager.getAccessory(intent);

                    // パーミッションがあるかチェック
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 接続を開く
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // Intent からアクセサリを取得
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if (accessory != null && accessory.equals(mAccessory)) {
                    // 接続を閉じる
                        Log.d("debug","accessary is closing");
                    closeAccessory();
                }
            }
        }
    };
    
    
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "starting onCreate()");
        ringbuf = new MyRingBuffer(BUF_SIZE, DATABYTE_SIZE);
        Log.d(TAG, "starting getInstance of UsbManager");
        // UsbManager のインスタンスを取得
        mUsbManager = UsbManager.getInstance(this);

        Log.d(TAG, "starting pendingIntent");
        // オレオレパーミッション用 Broadcast Intent
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // オレオレパーミッション Intent とアクセサリが取り外されたときの Intent を登録
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);
       
        Log.d(TAG, "starting view creation");
        setContentView(R.layout.main);
        registerButton = (Button) findViewById(R.id.regbutton);
        registerButton.setOnClickListener(this);
        unregisterButton = (Button) findViewById(R.id.unregbutton);
        unregisterButton.setOnClickListener(this);
        recordButton = (Button) findViewById(R.id.RecordBtn);
        idleButton = (Button) findViewById(R.id.IdleBtn);
        writeButton = (Button) findViewById(R.id.WriteBtn);
        sendButton = (Button) findViewById(R.id.SendBtn);
//        settingButton = (Button) findViewById(R.id.SettingBtn);        
        sysMessage = (TextView) findViewById(R.id.SysMessage);
        
        recordButton.setOnClickListener(this);
        idleButton.setOnClickListener(this);
        writeButton.setOnClickListener(this);
        sendButton.setOnClickListener(this);
//        settingButton.setOnClickListener(this);
        state = IDLE_STATE;
 
//        TextView textView = (TextView) findViewById(R.id.text_view);
//        textView.setText(C2DMessaging.getRegistrationId(this));

        
        mH = new Handler() {
            public void handleMessage(android.os.Message msg) {
            	Log.d(TAG,"handler");
                if (msg.getData().getBoolean("receivedMessageFlag")) {
                    String message = msg.getData().getString("receivedMessageString");
                   
 //                   Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                   
                    
 //1	                   TextView textView = (TextView) findViewById(R.id.message);
 //                   textView.setText(message);
                    String stateString = null;
                    if(getState() == IDLE_STATE){
                    	stateString = "IDLE_STATE";
                    }else if(getState() == RECORD_STATE){
                    	stateString = "RECORD_STATE";
                    }else if(getState() == WRITE_STATE){
                    	stateString ="WRITE_STATE";
                    }else if(getState() == SEND_STATE){
                    	stateString = "SEND_STATE";
                    }else if(getState() == SETTING_STATE){
                    	stateString = "SETTING_STATE";
                    }
                    String checkmes = "old state -> " + stateString + "new state ->" + message; 
                    if(message.equals("IDLE")){
                    	changeIntoIdle();
                    }else if(message.equals("RECORD")){
                    	Log.d("debug","come here RECORD");
                    	changeIntoRecord();
                    }else if(message.equals("WRITE")){
                    	changeIntoWrite();
                    }else if(message.equals("SEND")){
                    	changeIntoSend();
                    }
//                    }else if(message.equals("SETTING")){
//                    	changeState(SETTING_STATE);
//                    }
                    Log.d("debug", "message ="+ message);
                    Toast.makeText(getApplicationContext(), checkmes, Toast.LENGTH_SHORT).show();                   
                    
                }
            }
        };
        enableControls(false);

    }
    public void onClick(View v){
		if(v==registerButton){
			C2DMessaging.register(this, "kurimoto@k2-garage.com");
		}else if(v==unregisterButton){
			C2DMessaging.unregister(this);
		}else if(v == recordButton){
			changeIntoRecord();
/*                if(state == IDLE_STATE){
                        state = RECORD_STATE;
                        sysMessage.setText("start recording.....\n");
                }*/
        }else if(v == idleButton){
        	changeIntoIdle();
 /*               if(state == RECORD_STATE){
                        state = IDLE_STATE;
                        sysMessage.setText("stop recording....   state is idle\n");
                }else if(state == SEND_STATE && sending_flag == 0){
                        state = IDLE_STATE;
                        sysMessage.setText("stop sending....    state is idle\n");
                }*/
        }else if(v == writeButton){
        	changeIntoWrite();
 /*               if(state == IDLE_STATE){
                        state = WRITE_STATE;
                        writing_flag=1;
                        sysMessage.setText("start writing.....\n");
                        ringbuf.writeOutData("test.txt",getApplicationContext());
                        writing_flag=0;
                        sysMessage.setText("end of writing...\n  state is still WRITE");
                }*/
        }else if(v == sendButton){
        	changeIntoSend();
/*                if(state == WRITE_STATE && writing_flag == 0){
                        state = SEND_STATE;
                        writing_flag=1;
                        //FTP execution
                String uploadfile = "/data/data/" + this.getPackageName() + "/files/test.txt";
//                      String uploadfile = "/sdcard/test.txt";
                        try {  
                        //ファイルアップロード  
                        FileInputStream fis = new FileInputStream(uploadfile);    
                        Log.d("debug", "come 2");
                        FtpClientHelper.sendFile(HOSTNAME, FTP_PORT, FTPUSER, FTPPASSWD,   
                                "ftptest.txt", fis);
                        Log.d("debug", "come 3");
                    } catch (Exception e) {  
                        e.printStackTrace();  
                    }
               writing_flag=0;
                }*/
 /*       }else if(v == settingButton){
                
        }*/
        }
    }
    private void changeIntoRecord(){
        if(state == IDLE_STATE){
            state = RECORD_STATE;
            sysMessage.setText("start recording.....\n");
        }
    }
    private void changeIntoIdle(){
        if(state == RECORD_STATE){
            state = IDLE_STATE;
            sysMessage.setText("stop recording....   state is idle\n");
        }else if(state == SEND_STATE && sending_flag == 0){
            state = IDLE_STATE;
            sysMessage.setText("stop sending....    state is idle\n");
        }
    }
    private void changeIntoWrite(){
    	if(state == IDLE_STATE){
    		state = WRITE_STATE;
    		writing_flag=1;
    		sysMessage.setText("start writing.....\n");
    		ringbuf.writeOutData("test.txt",getApplicationContext());
    		writing_flag=0;
    		sysMessage.setText("end of writing...\n  state is still WRITE");
    	}
    }
    private void changeIntoSend(){
    	if(state == WRITE_STATE && writing_flag == 0){
    		state = SEND_STATE;
    		writing_flag=1;
    		//FTP execution
    		String uploadfile = "/data/data/" + this.getPackageName() + "/files/test.txt";
    		//      String uploadfile = "/sdcard/test.txt";
    		try {  
    			//ファイルアップロード  
    			FileInputStream fis = new FileInputStream(uploadfile);    
    			Log.d("debug", "come 2");
    			FtpClientHelper.sendFile(HOSTNAME, FTP_PORT, FTPUSER, FTPPASSWD,   
    					"ftptest.txt", fis);
    			Log.d("debug", "come 3");
    		} catch (Exception e) {  
    			e.printStackTrace();  
    		}
    		writing_flag=0;
    	}
    }
    
    
    @Override
    public void onResume() {
        super.onResume();

        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        // USB Accessory の一覧を取得
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            // Accessory にアクセスする権限があるかチェック
            if (mUsbManager.hasPermission(accessory)) {
                // 接続を開く
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        // パーミッションを依頼
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
//
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
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

            enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        enableControls(false);

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    private void enableControls(boolean enable) {
        if (enable) {
            sysMessage.setText("connected");
        } else {
            sysMessage.setText("not connected");
        }
//        mToggleButton.setEnabled(enable);
    }


    // ここでアクセサリと通信する
    @Override
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        byte[] record_buf = new byte[DATABYTE_SIZE];
        
        int i;

        Log.d(TAG,"start thread");
        while(true){
//              if(state == RECORD_STATE){
        // アクセサリ -> アプリ
//                      while (ret >= 0) {
                while(state == RECORD_STATE){
                        try {
                                ret = mInputStream.read(buffer);
                                Log.d("debug","reading byte num =" + ret);
                        } catch (IOException e) {
                                break;
                        }
                        i=0;
                        while(i + DATABYTE_SIZE <= ret){
                                for(int j=0;j<DATABYTE_SIZE;j++){
                                        record_buf[j] = buffer[j];
                                }
                                ringbuf.pushData(record_buf);
                                i=i+DATABYTE_SIZE;
                        }
                }
                try {
                                mInputStream.skip(ret);
                        } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }
                
                if(state == IDLE_STATE){
                        
                }else if(state == WRITE_STATE){
                        
                }else if(state == SEND_STATE){
                        
                }
                /*          i = 0;
                while (i < ret) {
                    int len = ret - i;

                    switch (buffer[i]) {
                        case 0x1:
                            // 2byte のオレオレプロトコル
                            // 0x1 0x0 や 0x1 0x1 など
                            if (len >= 2) {
                                Message m = Message.obtain(mHandler, MESSAGE_LED);
                                m.obj = new LedMsg(buffer[i + 1]);
                                mHandler.sendMessage(m);
                            }
                            i += 2;
                            break;

                        default:
                            Log.d(TAG, "unknown msg: " + buffer[i]);
                            i = len;
                            break;
                    }
                }
    */
            }
        }

    // アプリ -> アクセサリ
    public void sendCommand(byte command, byte value) {
        byte[] buffer = new byte[2];
        
        if(value != 0x1 && value != 0x0)
            value = 0x0;

        // 2byte のオレオレプロトコル
        // 0x1 0x0 や 0x1 0x1
        buffer[0] = command;
        buffer[1] = value;
        if (mOutputStream != null) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

    private int getState(){
    	return state;
    }
    private void changeState(int toState){
    	state = toState;
    }
    
    

}