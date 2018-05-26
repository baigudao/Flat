package com.taisau.flat.ui.main;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.orhanobut.logger.Logger;
import com.taisau.flat.FlatApplication;
import com.taisau.flat.R;
import com.taisau.flat.listener.OnConnectStatusChangeListener;
import com.taisau.flat.service.CompareService;
import com.taisau.flat.service.WhiteListService;
import com.taisau.flat.ui.BaseActivity;
import com.taisau.flat.ui.WelcomeActivity;
import com.taisau.flat.ui.history.HistoryListActivity;
import com.taisau.flat.ui.main.contract.MainContract;
import com.taisau.flat.ui.main.presenter.MainPresenter;
import com.taisau.flat.ui.personlist.PersonListActivity;
import com.taisau.flat.ui.setting.SettingActivity;
import com.taisau.flat.util.CameraUtils2;
import com.taisau.flat.util.Constant;
import com.taisau.flat.widget.FaceFrame;

import java.io.IOException;

/**
 * Created by whx on 2017/09/01
 * 主页面
 */
public class MainActivity extends BaseActivity implements SurfaceHolder.Callback, MainContract.View,
        Camera.ErrorCallback, OnConnectStatusChangeListener.OnConnectStatusChange {
    private static final String TAG = "MainActivity";
    private MainPresenter presenter;

    //承载画面的surfaceView
    public SurfaceView surfaceView;
    //用于承载预览画面生成图片的数据的holder
    public SurfaceHolder surfaceHolder;
    //相机的辅助工具类
    public CameraUtils2 cameraUtils;
    //底层帧布局
    public FrameLayout bottomLayout;

    //人脸框
    public FaceFrame frame;
    //public FaceStruct struct;
    private LinearLayout useLayout;
    private LinearLayout compareLayout;

    //身份证信息
    private TextView cardInfo;
    //身份证照片
    private ImageView cardImg;
    private ImageView faceImg;
    //比对结果信息
    private TextView resInfo, resResult;
    private ImageView resImg;
    private AlertDialog errorDialog;
    public Handler handler = new Handler();
    private CompareService compareService;
    private ImageView ivServerConnect;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化Present
        presenter = new MainPresenter(this);
        //初始化VIEW
        initView();
        Logger.d("onCreate: ");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Logger.d("onStart: ");
//        bindService(new Intent(this, CompareService.class), compareConnection, Context.BIND_AUTO_CREATE);
        compareLayout.setVisibility(View.GONE);
        presenter.updateAdsTitle();
        presenter.updateAdsSubitle();
        presenter.updateAdsPath();
        presenter.updateUserName();
        //初始化白名单
        //开启读卡器
//        IDCardUtils.getUtils().setIDCardReaderRun();
        //开启摄像头
//        cameraUtils = new CameraUtils2(this, this);
        surfaceView = (SurfaceView) findViewById(R.id.main_camera_preview);
//        Logger.d("w:"+surfaceView.getWidth()+" h:"+surfaceView.getHeight());
        surfaceHolder = surfaceView.getHolder();
        // 为surfaceHolder添加一个回调监听器
        //用于监听surfaceView的变化
        surfaceHolder.addCallback(this);
        surfaceView.setKeepScreenOn(true);
        if (cameraUtils == null) {
            cameraUtils = new CameraUtils2(this, this);
        }
        if (cameraUtils.getCamera() != null) {
            try {
                cameraUtils.getCamera().stopPreview();
                //绑定摄像头到surfaceView和Holder上
                //  cameraUtils.getCamera().autoFocus(null);
                cameraUtils.getCamera().setPreviewDisplay(surfaceHolder);
                cameraUtils.getCamera().setPreviewCallback(/*MainActivity.this*/presenter.getPreviewCallback());
                cameraUtils.getCamera().startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            presenter.initTime();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "输入服务器地址有误,请重新设置", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, SettingActivity.class);
            inputPasswordDialog(intent);
        }
        OnConnectStatusChangeListener.setOnConnectStatusChangeListener(this);
        if (FlatApplication.whiteListService != null) {
            if (WhiteListService.isTcpConnected) {
                ivServerConnect.setImageResource(R.mipmap.ic_server_connect);
            } else {
                ivServerConnect.setImageResource(R.mipmap.ic_server_disconnect);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        useLayout.setVisibility(View.VISIBLE);

        Logger.d("onResume: ");
        Display display = getWindow().getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        Logger.e("onCreate: point.width=" + point.x + ",point.y=" + point.y);//768*976
    }

    @Override
    protected void onPause() {
        super.onPause();

        Logger.d("onPause: ");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.d("onStop: ");
//        unbindService(compareConnection);
        presenter.stopTime();
        if (cameraUtils != null) {
            cameraUtils.releaseCamera();
            cameraUtils = null;
        }
        OnConnectStatusChangeListener.setOnConnectStatusChangeListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.e("MainActivity      onDestroy: ");
    }

    private void initView() {
        bottomLayout = (FrameLayout) findViewById(R.id.main_preview_back_frameLayout);
        /*
         * 底部布局
         */
        useLayout = (LinearLayout) findViewById(R.id.main_using_tips);
        compareLayout = (LinearLayout) findViewById(R.id.main_compare_info);

        findViewById(R.id.main_right_menu).setVisibility(View.VISIBLE);
        //懸停事件監聽左側菜單的顯示與隱藏
        //        bottomLayout.setOnHoverListener((v, event) -> {
        //            float x = event.getX();
        //            float y = event.getY();
        //            if (x >= 0 && x < 50 && y > 140 && y < 370)
        //                findViewById(R.id.main_right_menu).setVisibility(View.VISIBLE);//VISIBLE
        //            else
        //                findViewById(R.id.main_right_menu).setVisibility(View.GONE);
        //            return false;
        //        });


//        findViewById(R.id.main_right_btn1).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (FlatApplication.getApplication().getDaoSession().getHistoryListDao().count() == 0) {
//                    Toast.makeText(MainActivity.this, "没有历史记录", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//                Intent intent = new Intent(MainActivity.this, PrintHistoryActivity.class);
//                intent.putExtra("his_id", Long.parseLong(Preference.getHisLastId()));
//                inputPasswordDialog(intent);
//            }
//        });
        findViewById(R.id.main_right_btn2).setOnClickListener(v ->
                inputPasswordDialog(new Intent(MainActivity.this, PersonListActivity.class)));
        findViewById(R.id.main_right_btn3).setOnClickListener(v ->
                inputPasswordDialog(new Intent(MainActivity.this, HistoryListActivity.class)));
        findViewById(R.id.main_right_btn4).setOnClickListener(v ->
                inputPasswordDialog(new Intent(MainActivity.this, SettingActivity.class)));
        /*
         *  compare info比对信息
         */
//        pager = (ViewPager) findViewById(R.id.main_ads_pager);
        faceImg = (ImageView) findViewById(R.id.main_compare_img1);
        cardImg = (ImageView) findViewById(R.id.main_compare_img2);
        resInfo = (TextView) findViewById(R.id.main_compare_info_res);
        resInfo.setVisibility(View.GONE);
        resResult = (TextView) findViewById(R.id.main_compare_info_result);
        resResult.setVisibility(View.GONE);
        resImg = (ImageView) findViewById(R.id.main_compare_info_img);
        resImg.setVisibility(View.GONE);
        cardInfo = (TextView) findViewById(R.id.main_compare_info_cardInfo);

        /*
         * 人脸框架
         */
        frame = new FaceFrame(this);
        ivServerConnect = (ImageView) findViewById(R.id.iv_server_connect);
//        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottomLayout.getLayoutParams();
//        params.width = params.width
//        bottomLayout.setScaleX(5/2f);
//        bottomLayout.setScaleY(2.5f);

    }

    @Override
    public void onBackPressed() {
    }

    private void inputPasswordDialog(final Intent intent) {
//        if (passwardDialog == null) {
//            presenter.setRunDetect(false);
//            final EditText editText = new EditText(this);
//            editText.setSingleLine(true);
//            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
////        editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);//不管用
//            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
//            editText.setHint("密码");
//            passwardDialog = new AlertDialog.Builder(this).setTitle("验证密码")
//                    .setMessage("请输入密码，验证成功才能跳转页面")
//                    .setView(editText)
//                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            String password = editText.getText().toString();
//                            if (password.equals("6116")) {
//                                startActivity(intent);
//                            } else {
//                                Toast.makeText(MainActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
//                                hideSystemUi();
//                            }
//                            passwardDialog = null;
//                            presenter.setRunDetect(true);
//                        }
//                    })
//                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            hideSystemUi();
//                            passwardDialog = null;
//                            presenter.setRunDetect(true);
//                        }
//                    })
//                    .setCancelable(false)
//                    .show();
//        }
        startActivity(intent);
    }

    //surfaceView生成时会调用此函数
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        if (cameraUtils.getCamera() != null) {
            try {
                cameraUtils.getCamera().stopPreview();
                //绑定摄像头到surfaceView和Holder上
                //  cameraUtils.getCamera().autoFocus(null);
                cameraUtils.getCamera().setPreviewDisplay(surfaceHolder);
                cameraUtils.getCamera().setPreviewCallback(/*MainActivity.this*/presenter.getPreviewCallback());
                cameraUtils.getCamera().startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //surfaceView销毁时会调用此函数此时释放摄像头
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }


    @Override
    public void updateTimeStatus(String time) {
//        this.time.setText(time);
    }

    @Override
    public void updateAdsTitle(String title) {
//        main_tittle.setText(title);
    }

    @Override
    public void updateAdsSubtitle(String subtitle) {
//        sub_tittle.setText(subtitle);
    }


    @Override
    public void updateAdsImage(String[] paths) {
//        if (paths.length == 1 && paths[0] == null) {
//            Log.d( "updateAdsImage: paths = null");
//            adsCount = 1;
//        } else {
//            for (String path : paths) {
//                Log.d( "updateAdsImage: paths[i] = " + path);
//            }
//            if (paths.length < 1) {
//                adsCount = 1;
//            } else {
//                adsCount = paths.length;
//            }
//        }
//
//        LayoutInflater inflater = getLayoutInflater();
//        ImageView[] view = new ImageView[adsCount];
//        ArrayList<ImageView> viewList = new ArrayList<>(adsCount);// 将要分页显示的View装入数组中
//        final ViewGroup viewGroup = null;
//        for (int i = 0; i < adsCount; i++) {
//            view[i] = (ImageView) inflater.inflate(R.layout.content_main_ads, viewGroup);
//            if (i == 0) {//第一页放置默认或者客户设置的广告
//                if (Preference.getAdsPath() == null || Preference.getAdsPath().equals("")) {
//                    view[i].setImageResource(R.mipmap.main_ads_default);
//                } else {
//                    view[i].setImageBitmap(BitmapFactory.decodeFile(Preference.getAdsPath()));
//                }
//            } else {//剩下的放置下发的广告
//                view[i].setImageBitmap(BitmapFactory.decodeFile(paths[i]));
//            }
//            viewList.add(view[i]);
//        }
//        AdsPagerAdapter adsPagerAdapter = new AdsPagerAdapter(viewList);
//        pager.setAdapter(adsPagerAdapter);
//        pagerIndex = 0;
//        pager.setCurrentItem(pagerIndex);
//        if (adsCount < 2) {
//            return;
//        }
//        subscription = Observable.interval(12, TimeUnit.SECONDS)
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Action1<Long>() {
//                    @Override
//                    public void call(Long aLong) {
//                        if (pagerIndex < (adsCount - 1)) {
//                            pagerIndex++;
//                        } else {
//                            pagerIndex = 0;
//                        }
//                        Log.d( "call: pagerIndex=" + pagerIndex);
//                        pager.setCurrentItem(pagerIndex);
//                    }
//                });
    }

    @Override
    public void updateFaceStruct(/*GFace.FacePointInfo*/String info, int pic_width, int pic_height) {
      /*  if (pic_width == 0 || pic_height == 0) {
            struct.clearPoint(bottomLayout);
        }
        struct.drawView(info, bottomLayout, pic_width, pic_height);*/
    }

    @Override
    public void updateFaceFrame(long[] position, int pic_width, int pic_height) {
        frame.createFrame(bottomLayout, position, pic_width, pic_height);
    }

    @Override
    public void updateUserName(String result) {
        //custom_name.setText(result);
    }

    Toast toast;

    @Override
    public void showToastMsg(String msg) {
        if (toast == null) {
            toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        } else {
            toast.setText(msg);
            toast.setDuration(Toast.LENGTH_SHORT);
        }
        toast.show();
    }

    @Override
    public void setCompareLayoutVisibility(int visitable) {
        if (visitable == View.VISIBLE) {
            compareLayout.setVisibility(View.VISIBLE);
//            useLayout.setVisibility(View.GONE);
        } else {
            compareLayout.setVisibility(View.GONE);
//            useLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void updateCompareRealRes(Bitmap real) {
        faceImg.setImageBitmap(real);
    }

    public static boolean isShowCardRes = false;

    @Override
    public void updateCompareCardRes(Bitmap card) {
        cardImg.setImageBitmap(card);
        cardImg.invalidate();
        isShowCardRes = true;
    }

    @Override
    public void updateCompareResultInfo(String result, int textColor) {
        cardInfo.setText(result);
//        cardInfo.invalidate();
    }

    @Override
    public void updateCompareResultScore(String result, int visitable) {//对比分值,包含“核查通过（白名单自动比对）”显示绿色
        if (!"start".equals(result) && visitable == View.GONE) {
            if (!Constant.isDoubleScreen) {
//                startNewActivity(this, "com.taisau.nfcdm");
                startNewActivity(this, "com.ebsl.AttendanceApp");
            }
        } else if (visitable != View.GONE) {
            if (result.equals(getString(R.string.person_ic_no_accord))) {//人卡不符
                resResult.setText(R.string.compare_fail);
                resResult.setTextColor(getResources().getColor(R.color.color_f73030));
                resInfo.setTextColor(getResources().getColor(R.color.color_f73030));
            } else {//比对通过
                resResult.setText(R.string.compare_pass);
                resResult.setTextColor(getResources().getColor(R.color.color_0dd63c));
                resInfo.setTextColor(getResources().getColor(R.color.color_0dd63c));
            }
            resInfo.setText(result);
        }
    }

    @Override
    public void updateCompareResultImg(int resId, int visitable) {
        resImg.setVisibility(visitable);
        resResult.setVisibility(visitable);
        resInfo.setVisibility(visitable);
        if (visitable == View.GONE) return;
        resImg.setImageResource(resId);
//        if(resId==R.mipmap.compare_fail_error_man){
//            resResult.setText(R.string.compare_fail);
//            resResult.setTextColor(getResources().getColor(R.color.color_f73030));
//        }
    }

    @Override
    public void updateSoundStatus(int soundNum) {
//        int current = seekbar_sound.getProgress();
//        Log.d( "updateSoundStatus: current = " + current + ",soundNum=" + soundNum);
//        switch (soundNum) {
//            case 0: //静音
//                seekbar_sound.setProgress(0);
//                break;
//            case -1: //音量 +1
//
//                if (current != maxSound) {
//                    seekbar_sound.setProgress(current + 1);
//                }
//
//                break;
//            case -2: //音量 -1
//                if (current != 0) {
//                    seekbar_sound.setProgress(current - 1);
//                }
//                break;
//            case -3: //音量最大
//                if (current != maxSound) {
//                    seekbar_sound.setProgress(maxSound);
//                }
//                break;
//            default: // 直接设置音量
//
//                break;
//        }
    }


    @Override
    public void onError(int error, Camera camera) {
        if (error == 100) {
            errorDialog = new AlertDialog.Builder(this).setTitle("摄像头失效")
                    .setMessage("请确认摄像头是否有效，或者重新插拔摄像头点击确定键重新初始化\n提示:5秒后会自动进行初始化工作")
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (errorDialog != null && errorDialog.isShowing()) {
                            errorDialog.dismiss();
                            if (cameraUtils != null) {
                                cameraUtils.releaseCamera();
                                cameraUtils = null;
                                cameraUtils = new CameraUtils2(MainActivity.this, MainActivity.this);
                                try {
                                    cameraUtils.getCamera().stopPreview();
                                    //绑定摄像头到surfaceView和Holder上
                                    //  cameraUtils.getCamera().autoFocus(null);
                                    cameraUtils.getCamera().setPreviewDisplay(surfaceHolder);
                                    cameraUtils.getCamera().setPreviewCallback(presenter.getPreviewCallback());
                                    cameraUtils.getCamera().startPreview();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            surfaceView.setVisibility(View.GONE);
                            surfaceView.setVisibility(View.VISIBLE);
                        }
                    })
                    .setNegativeButton("关闭应用", (dialog, which) -> {
                        Intent intent = new Intent();
                        intent.setClass(MainActivity.this, WelcomeActivity.class);
                        intent.putExtra("exit_flag", "exit_id");
                        MainActivity.this.startActivity(intent);
                    })
                    .setCancelable(false)
                    .show();
        }
        handler.postDelayed(() -> {
            if (errorDialog != null && errorDialog.isShowing()) {
                errorDialog.dismiss();
                if (cameraUtils != null) {
                    cameraUtils.releaseCamera();
                    cameraUtils = null;
                    cameraUtils = new CameraUtils2(MainActivity.this, MainActivity.this);
                    try {
                        cameraUtils.getCamera().stopPreview();
                        //绑定摄像头到surfaceView和Holder上
                        //  cameraUtils.getCamera().autoFocus(null);
                        cameraUtils.getCamera().setPreviewDisplay(surfaceHolder);
                        cameraUtils.getCamera().setPreviewCallback(presenter.getPreviewCallback());
                        cameraUtils.getCamera().startPreview();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                surfaceView.setVisibility(View.GONE);
                surfaceView.setVisibility(View.VISIBLE);
            }
        }, 5000);
    }

    private ServiceConnection compareConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            compareService = ((CompareService.CompareBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            compareService = null;
        }
    };

    @Override
    public void OnServerChange(final boolean connected) {
//        if (connected && ivServerConnect.getVisibility() == View.VISIBLE)
//            return;
//        if (!connected && ivServerConnect.getVisibility() == View.GONE) return;
//        runOnUiThread(() -> ivServerConnect.setVisibility(connected ? View.VISIBLE : View.GONE));
        runOnUiThread(() -> {
            if (connected) {
                ivServerConnect.setImageResource(R.mipmap.ic_server_connect);
            } else {
                ivServerConnect.setImageResource(R.mipmap.ic_server_disconnect);
            }
        });
    }

    private AlertDialog pd;
    private boolean hadShow;

    @Override
    public void OnUsbChange(final boolean connected) {
        if (connected && findViewById(R.id.iv_usb_connect).getVisibility() == View.VISIBLE) return;
        if (!connected && findViewById(R.id.iv_usb_connect).getVisibility() == View.GONE) return;
        runOnUiThread(() -> {
            findViewById(R.id.iv_usb_connect).setVisibility(connected ? View.VISIBLE : View.GONE);
            if (connected && pd == null && !hadShow) {
                hadShow = true;
                pd = new AlertDialog.Builder(MainActivity.this).create();
                pd.setMessage(getString(R.string.tips_connect_usb));
                pd.show();
                new Handler().postDelayed(() -> {
                    if (pd != null && pd.isShowing()) {
                        pd.dismiss();
                        pd = null;
                        hadShow = false;
                    }
                }, 3000);

            }
        });
    }

    public void startNewActivity(Context context, String packageName) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            // Bring user to the market or let them choose an app?
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName(packageName, "com.taisau.flat.ui.main.MainActivity"));
            //            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.putExtra("skip", "skip");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
