package com.taisau.flat.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.orhanobut.logger.Logger;
import com.taisau.flat.R;
import com.taisau.flat.ui.main.MainActivity;
import com.taisau.flat.util.Preference;


public class WelcomeActivity extends BaseActivity {
    private LinearLayout llView;
    private RelativeLayout rlKey;
    private EditText etKey, etName, etIp;
    private RadioButton rbOut, rbIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
//        initView();
        initData();
        Logger.e("WelcomeActivity    onCreate");
    }

    private void initView() {
        llView = (LinearLayout) findViewById(R.id.ll_view);
        rlKey = (RelativeLayout) findViewById(R.id.rl_key);
        etKey = (EditText) findViewById(R.id.et_key);
        etName = (EditText) findViewById(R.id.et_user_name);
        etIp = (EditText) findViewById(R.id.et_server_ip);
        etIp.setInputType(InputType.TYPE_CLASS_PHONE);
        rbOut = (RadioButton) findViewById(R.id.rb_doorway_out);
        rbIn = (RadioButton) findViewById(R.id.rb_doorway_in);
        Button btnStart = (Button) findViewById(R.id.btn_start);
        if (Preference.getDoorway() == null || Preference.getDoorway().equals("") ||
                Preference.getDoorway().equals("1")) {
            rbIn.setChecked(true);
        } else {
            rbOut.setChecked(true);
        }
       /* if (Preference.getMachineKey() == null || Preference.getMachineKey().equals("")) {
            Logger.e("WelcomeActivity    機器碼    為空");
            String sn = GFace.GetSn("TS");
            FileUtils.wirteToFile(LIB_DIR + "/key.txt", sn);
            llView.setVisibility(View.VISIBLE);
            rlKey.setVisibility(View.VISIBLE);
        } else {*/
//            if (getIntent().getStringExtra("exit_flag") == null) {
//                Logger.e("WelcomeActivity    機器碼   不為空");
//                Logger.e("setkey : " + GFace.SetKey(/*"B052C8C5A91673242EFB"*/Preference.getMachineKey()));
//                int res = GFace.loadModel(LIB_DIR + "/face_GFace6/dnew.dat", LIB_DIR + "/face_GFace6/anew.dat", LIB_DIR + "/face_GFace7/db.dat", LIB_DIR + "/face_GFace7/p.dat");
//                Logger.d("模型加載狀態：" + res);
//                if (res != 0) {
//                    Preference.setMachineKey(null);
//                    Logger.d(" Preference.setMachineKey(null)");
//                    Toast.makeText(WelcomeActivity.this,
//                            getString(R.string.algorithm_key_error), Toast.LENGTH_SHORT).show();
//                    finish();
//                }
//            }
        etKey.setText(Preference.getMachineKey());
        rlKey.setVisibility(View.GONE);
        if (Preference.getServerIp() == null || Preference.getServerIp().equals("")) {
            llView.setVisibility(View.VISIBLE);
        } else {
            llView.setVisibility(View.GONE);
        }
//        }

        btnStart.setOnClickListener(view -> {
            if (/*etKey.getText() != null && !etKey.getText().toString().equals("") &&*/
                    etName.getText() != null && !etName.getText().toString().equals("") &&
                            etIp.getText() != null && !etIp.getText().toString().equals("")) {
                boolean success = etIp.getText().toString().matches(
                        "((?:(?:25[0-5]|2[0-4]\\d|(?:1\\d{2}|[1-9]?\\d))\\.){3}" +
                                "(?:25[0-5]|2[0-4]\\d|(?:1\\d{2}|[1-9]?\\d)))");
                if (success) {
                    Preference.setServerIp(etIp.getText().toString());
                    Preference.setDoorway(rbIn.isChecked() ? "1" : "2");
                    Preference.setCustomName(etName.getText().toString());

//                    if (Preference.getMachineKey() == null || Preference.getMachineKey().equals("")) {
//                        Preference.setMachineKey(etKey.getText().toString());
//                        Logger.d("输入Key: " + etKey.getText().toString());
//                        Logger.e("setkey : " + GFace.SetKey(/*"B052C8C5A91673242EFB"*/Preference.getMachineKey()));
//                        int res = GFace.loadModel(LIB_DIR + "/face_GFace6/dnew.dat", LIB_DIR + "/face_GFace6/anew.dat", LIB_DIR + "/face_GFace7/db.dat", LIB_DIR + "/face_GFace7/p.dat");
//                        Logger.d("模型加載狀態：" + res);
//                        if (res == 0) {
//                            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
//                            finish();
//                        } else {
//                            Preference.setMachineKey(null);
//                            Logger.d(" Preference.setMachineKey(null)");
//                            Toast.makeText(WelcomeActivity.this,
//                                    getString(R.string.algorithm_key_error), Toast.LENGTH_SHORT).show();
//                        }
//                    } else {
                    startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                    finish();
//                    }
                } else {
                    Toast.makeText(WelcomeActivity.this,
                            getString(R.string.error_format), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(WelcomeActivity.this,
                        getString(R.string.configuration_null), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initData() {
        if (Preference.getServerIp() == null || Preference.getServerIp().equals("")) {
            Preference.setAgeWarning("false");
            Preference.setAliveCheck("true");
            Preference.setScoreRank("easy");
            Preference.setVoiceTips("false");
            Preference.setNoFaceCount("20");
            Preference.setAgeWarningMAX("18");
            Preference.setAgeWarningMIN("0");
            Preference.setServerPort("8899");
            Preference.setGatePort("9010");
            Preference.setFirstTime("0");//初始化App后，连接服务器会发送获取所有名单的命令
            Preference.setDoorway("1");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.e("WelcomeActivity    onResume");
//                Logger.e("key: "+ GFace.GetSn("TS"));
//        Logger.e("setkey : "+  GFace.SetKey("B052C8C5A91673242EFB"));
        if (getIntent().getStringExtra("skip") != null) {
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            Logger.e("WelcomeActivity    onResume     跳过1秒休眠");
            finish();
        } else {
            new Handler().postDelayed(() -> {
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            }, 500);
        }
//        Logger.e("WelcomeActivity    onResume     over");
//        if (llView.getVisibility() == View.GONE) {
//            Logger.e("WelcomeActivity   view不顯示");
//            new Handler().postDelayed(() -> {
//                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
//                finish();
//            }, 1000);
//        }
//        String serialNum = Build.SERIAL;
//        String deviceManufacturer = Build.MANUFACTURER;
//        String deviceModel = Build.MODEL;
//        String deviceBrand = Build.BRAND;
//        String device = Build.DEVICE;
//
//        Log.e("Identifier_Serial_Num", validate(serialNum));
//        Log.e("Identifier_Manufacturer", validate(deviceManufacturer));
//        Log.e("Identifier_Model", validate(deviceModel));
//        Log.e("Identifier_Brand", validate(deviceBrand));
//        Log.e("Identifier_Device", validate(device));
/*Identifier_Serial_Num: unknown     双屏主板
Identifier_Manufacturer: rockchip
Identifier_Model: rk3288_box
Identifier_Brand: Android
Identifier_Device: rk3288_box*/
/*
Identifier_Serial_Num: P1QRMBUAKN  泰首身证通主板
Identifier_Manufacturer: rockchip
Identifier_Model: rk3288
Identifier_Brand: Android
Identifier_Device: rk3288*/
        Logger.e("Devices id = "+getDeviceId(this));
//        泰首身证通主板 Devices id =  null
        //双屏主板 Devices id =  357942051433177

        Logger.e("android id  = " + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        //泰首身证通主板 android id =  83e2ddd7b897422
        //双屏主板 android id =  516fb4abcb6ceb59
    }
    public static String getDeviceId(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getDeviceId();
    }
    //判空
    private String validate(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }
}
