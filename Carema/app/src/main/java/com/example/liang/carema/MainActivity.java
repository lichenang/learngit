package com.example.liang.carema;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class MainActivity extends Activity  implements SurfaceHolder.Callback{

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    static final int USBRQ_HID_GET_REPORT=0x01;
    static final int USBRQ_HID_SET_REPORT=0x09;

    private Camera mCamera;//相机
    //UI 上面 的 控件
    private TextView usbDescibetextView=null,usbdatatextView=null,txtTest,txtValue;
    private Button btnScan;
    private SeekBar barValue;
    private Switch swCtrl,swOpen;
    boolean isCtrl=false,isOpen=false;//控制Switch按钮的控制
    private static final String TAG = "USB-host";
    UsbManager manager,mUsbMng=null;  //USB管理者
    private UsbDevice device=null,mUsbDev=null;
    UsbInterface[] usbinterface=null;
    UsbInterface  mUsbInf=null;
    UsbEndpoint[][] endpoint=new UsbEndpoint[5][5];
    UsbEndpoint mUsbEP;
    UsbDeviceConnection connection=null,mUsbConn=null;
    byte[] mybuffer=new byte[1024];
    int value=0;//滑动条的值
    private int myvid=1155,mypid=22352;
    ConnectedThread mconnectedthread=null;  //声明链接线程对象
    boolean   threadsenddata=false;//线程发送数据控制位
    boolean threadcontrol_ct=false;

    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;









    private Handler mHandler = new Handler() {
        public void handleMessage (Message msg) {//此方法在ui线程运行

        }
    };
    //广播事件
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            finish();
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
      //  setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        UI_Init();//初始化控件
        init();
        //开关 用于打开灯
        swOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (swOpen.isChecked()){
                    isOpen=true;   //灯被打开

                }else {
                    isOpen=false;
                }
                threadsenddata=true;
            }
        });

        //Switch开关  亮度控制按钮
        swCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (swCtrl.isChecked()){
                    isCtrl=true;
                }else {
                    isCtrl=false;
                }
                threadsenddata=true;
            }
        });
        //扫描按钮的点击事件
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //USB的一些信息列表
                ArrayList<String> list=ListUsbDev();
                for(int i = 0;i < list.size(); i ++){
                    txtTest.append(list.get(i)+"\n-------\n");
                    Log.d(TAG,list.get(i));
                }
            }
        });
        //亮度控制滑动进度条的 滑动事件
        barValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                //获取滑动的值
                value = seekBar.getProgress();
                threadsenddata=true;  //线程发送数据
                txtValue.setText(value+"");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        //  设置组件乐意接受的Intent
        IntentFilter filter = new IntentFilter("android.hardware.usb.action.USB_DEVICE_DETACHED");
        registerReceiver(mUsbReceiver, filter);
        //获取USB设备
        getUSB();


    }


    //  初始化    SurfaceView
    private void init(){
        mSurfaceView=(SurfaceView) findViewById(R.id.surfaceView);
        //获取触屏的大小
        Display display = getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
        mSurfaceHolder=mSurfaceView.getHolder();
        lp.height = height/2;
        lp.width = width;
        mSurfaceView.setLayoutParams(lp);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //设置该SurfaceView是一个"推送"类型的SurfaceView
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(this);
    }


    //捕获照片的方法
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            //将图片保存至相册
            ContentResolver resolver = getContentResolver();
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            MediaStore.Images.Media.insertImage(resolver, bitmap, "t", "des");
            //拍照后重新开始预览
            camera.startPreview();
        }
    };





    //添加按钮监听事件   捕获照片   -----  即   点击拍照
    public  void buttonCapture(View view){
        //从相机中获取图像
        mCamera.takePicture(null, null, mPicture);
        Toast.makeText(MainActivity.this, " 拍照按钮", Toast.LENGTH_SHORT).show();

        //延时 从拍
        new Handler().postDelayed(new Runnable(){
            public void run() {
                //打开灯
                isOpen = true;
                threadsenddata=true;
                Toast.makeText(MainActivity.this, "打开灯", Toast.LENGTH_SHORT).show();
                //从相机中获取图像
                mCamera.takePicture(null, null, mPicture);
                Toast.makeText(MainActivity.this, "再次拍照完成", Toast.LENGTH_SHORT).show();
           //     for(int i=0;i<1000;i++){

           //     }
                threadsenddata = false;
                isOpen = false;

            }
        }, 1000);
        Toast.makeText(MainActivity.this, "释放相机", Toast.LENGTH_SHORT).show();

    }
    //初始化界面
    public void UI_Init(){
        usbDescibetextView=(TextView)findViewById(R.id.usbDescribtextView);
        btnScan=(Button)findViewById(R.id.btnScan); //扫描按钮
        txtTest=(TextView)findViewById(R.id.txtTest);
        barValue=(SeekBar)findViewById(R.id.barValue);//亮度控制的滑动进度条   0--15
        swCtrl=(Switch)findViewById(R.id.swCtrl);// 开关 Switch  亮度控制按钮
        swOpen=(Switch)findViewById(R.id.swOpen);//开关 Switch 按钮
        txtValue=(TextView)findViewById(R.id.txtValue);//显示亮度级别
        swCtrl.setChecked(false);//默认 控制 按钮关闭
        swOpen.setChecked(false);//默认 开关 按钮关闭
        barValue.setProgress(7); // 默认 度控制条的值为
    }



    //获取USB设备
    private void  getUSB(){
        //枚举设备
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);//获取系统USB设备

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();//获取USB列表

        System.out.println("get device list  = " + deviceList.size());
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        //设备列表为空
        if (deviceList.isEmpty()||manager==null){
            finish();
            Toast.makeText(this, "没有设备", Toast.LENGTH_LONG).show();
        }
        //遍历 链接的 USB设备
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            Log.d(TAG, "vid: " + device.getVendorId() + "\t pid: " + device.getProductId());

            usbDescibetextView.setText("找到设备:"+ device.getVendorId() +
                    "\t pid: " + device.getProductId());
            break;
        }
        // 如果设备 不为空
        if(device!=null){
           //打印日志
            Log.d(TAG,"找到设备:"+ device.getVendorId() +
                    "\t pid: " + device.getProductId());
        }
        else{
            Log.d(TAG,"未发现支持设备");
            finish();
            return;
        }
        // 获取广播    USB活动许可
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        if(manager.hasPermission(device)){
        }
        else{
            manager.requestPermission(device, pi);
        }

        if(manager.hasPermission(device)){
            usbDescibetextView.setText(usbDescibetextView.getText()+"\n拥有访问权限");
        }
        else{
            usbDescibetextView.setText(usbDescibetextView.getText()+"\n未获得访问权限");
        }

        usbDescibetextView.setText(usbDescibetextView.getText()
                +"\n"+device.getDeviceName());

        usbDescibetextView.setText(usbDescibetextView.getText()
                +"\n接口数为："+device.getInterfaceCount());

        usbinterface=new UsbInterface[device.getInterfaceCount()];
        for(int i=0;i<device.getInterfaceCount();i++){
            usbinterface[i]=device.getInterface(i);
            usbDescibetextView.setText(usbDescibetextView.getText()
                    +"\n接口"+i+"的端点数为："+usbinterface[i].getEndpointCount());
            for(int j=0;j<usbinterface[i].getEndpointCount();j++){
                endpoint[i][j]=usbinterface[i].getEndpoint(j);
                if(endpoint[i][j].getDirection()== UsbConstants.USB_DIR_OUT){
                    usbDescibetextView.setText(usbDescibetextView.getText()
                            +"\n端点"+j+"-OUT");
                }
                else{
                    usbDescibetextView.setText(usbDescibetextView.getText()
                            +"\n端点"+j+"- IN");
                }
            }
        }
        //判断USB链接对象是否存在
        if(mconnectedthread!=null){
            mconnectedthread=null;
        }
        //创建USB链接对象
        mconnectedthread= new ConnectedThread();
        mconnectedthread.start();//开启链接线程
        //第一次控制
        threadsenddata=true;
    }




    //列出所有USB设备
    private  ArrayList<String> ListUsbDev(){
        //获取USB管理者
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        UsbDevice mDev=null;
        UsbInterface mInf=null;
        UsbEndpoint mEp=null;
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        ArrayList<String> devList = new ArrayList<String>();
        String tmpStr="";
        while (deviceIterator.hasNext()) {
            mDev = deviceIterator.next();
            tmpStr+=(mDev.getDeviceName() + "\t vid:" +mDev.getVendorId() + "\t pid:" + mDev.getProductId()
                    +"\n\t接口数为:"+mDev.getInterfaceCount());
            for(int i=0;i<mDev.getInterfaceCount();i++){
                mInf=mDev.getInterface(i);
                tmpStr+=("\n\t\t接口"+i+"的端点数为:"+mInf.getEndpointCount());
                for(int j=0;j<mInf.getEndpointCount();j++){
                    mEp=mInf.getEndpoint(j);
                    if(mEp.getDirection()== UsbConstants.USB_DIR_OUT){
                        tmpStr+=("\n\t\t\t端点"+j+"-OUT");
                    } else{
                        tmpStr+=("\n\t\t\t端点"+j+"- IN");
                    }
                }
            }
            devList.add(tmpStr);
        }
        return devList;
    }











     //销毁方法
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub

        threadcontrol_ct=false;
        Toast.makeText(this, "USB设备被拔出", Toast.LENGTH_LONG).show();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera=Camera.open();
        Camera.Parameters parameters=mCamera.getParameters();

        //操作1 为相机设置某种特效
        List<String> colorEffects=parameters.getSupportedColorEffects();
        Iterator<String> iterator1=colorEffects.iterator();
        while (iterator1.hasNext()) {
            String effect = (String) iterator1.next();
            if (effect.equals(Camera.Parameters.EFFECT_SOLARIZE)) {
                //若支持过度曝光效果,则设置该效果
                parameters.setColorEffect(Camera.Parameters.EFFECT_SOLARIZE);
                break;
            }
        }
        //操作3 当屏幕变化时,旋转角度.否则不对
        mCamera.setDisplayOrientation(90);

        //操作结束
        try {
            //将摄像头的预览显示设置为mSurfaceHolder
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            mCamera.release();
        }
        //设置输出格式
        parameters.setPictureFormat(PixelFormat.JPEG);
        //设置摄像头的参数.否则前面的设置无效
        mCamera.setParameters(parameters);
        //摄像头开始预览
        mCamera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
    }


    //连接进程
class ConnectedThread extends Thread{

    @Override
    public void destroy() {
        // TODO Auto-generated method stub
        super.destroy();
    }
    // 线程类的构造方法   用于链接 USB
    public ConnectedThread(){
        //
        if(connection!=null){
            connection.close();
        }
        connection = manager.openDevice(device);
        if (connection.claimInterface(usbinterface[0], true)) {
            threadcontrol_ct = true;
        }else{
            threadcontrol_ct=false;
            Log.d(TAG,"打开接口失败");
        }
    }
    // 发送数据   利用wIndexd的低字节传输数据
    private void sendByte(int data){
            /*利用wIndexd的低字节传输数据*/
        connection.controlTransfer(UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS, USBRQ_HID_SET_REPORT, 0x00,0x00FF&data,null,0,200);
    }
    // 读取(接收)数据
    private int readByte(){
            /*利用byte接收数据，没有接收到返回0*/
        byte[] buffer=new byte[10];
        connection.controlTransfer(UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS, USBRQ_HID_GET_REPORT, 0x00,0x00,buffer,1,200);
        return ((int) buffer[0]);
    }



    @Override// 线程里面的 run()方法
    public void run() {
        // TODO Auto-generated method stub
        int datalength;
        while(threadcontrol_ct){
            if(threadsenddata){
                threadsenddata=false;
//                    connection.bulkTransfer(endpoint[0][1], mytmpbyte, mytmpbyte.length, 30);
//                    connection.controlTransfer(UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS, USBRQ_HID_SET_REPORT, 0x00,0x0001,null,0,100);
                if (isCtrl){
                    sendByte(value);
                } else {
                    if (isOpen) {
                        sendByte(15);
                    }else {
                        sendByte(0);
                    }
                }
//                    System.out.println("IN-OUT:"+readByte());
                System.out.println("Send");
            }
        }
    }
}

}
