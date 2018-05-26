package com.taisau.flat.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.serialport.api.SerialPort;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;
import android.util.Base64;
import android.widget.Toast;

import com.google.gson.Gson;
import com.orhanobut.logger.Logger;
import com.taisau.flat.FlatApplication;
import com.taisau.flat.R;
import com.taisau.flat.bean.History;
import com.taisau.flat.bean.HistoryDao;
import com.taisau.flat.bean.Person;
import com.taisau.flat.bean.PersonDao;
import com.taisau.flat.listener.ConfigChangeListener;
import com.taisau.flat.listener.OnCardDetectListener;
import com.taisau.flat.listener.OnConnectStatusChangeListener;
import com.taisau.flat.listener.OnServerSettingChangeListener;
import com.taisau.flat.listener.SaveAndUpload;
import com.taisau.flat.ui.main.MainActivity;
import com.taisau.flat.ui.main.model.MainModel;
import com.taisau.flat.util.Constant;
import com.taisau.flat.util.FeaUtils;
import com.taisau.flat.util.FileUtils;
import com.taisau.flat.util.ImgUtils;
import com.taisau.flat.util.Preference;
import com.taisau.flat.util.ThreadPoolUtils;
import com.taisau.flat.util.XmlUtil;
import com.taisau.msghandler.MsgManager;
import com.taisau.msghandler.zj.WANMsgBean;
import com.taisau.msghandler.zj.WANMsgConstant;
import com.taisau.signalrandroidsdk.SignalRManager;
import com.taisau.signalrandroidsdk.listener.SignalRStatusListener;

import org.greenrobot.greendao.query.QueryBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

import static com.taisau.flat.util.Constant.TEMPLATE_FEA;
import static com.taisau.flat.util.Constant.TEMPLATE_IMG;


public class WhiteListService extends Service implements SaveAndUpload.OnSaveAndUploadListener
        , OnServerSettingChangeListener.OnServerSettingChange {
    private final WhiteListBinder binder = new WhiteListBinder();
    private boolean isRun;
    private Handler handler = new Handler();
    private final int MSG_CMD = 2;  //协议中固定使用，后续有调整在改

    String sn = android.os.Build.SERIAL; //设备序列号，双屏设备返回unknown，换算法时需要序列号，双屏rom已经支持

//    String sn = Settings.Secure.getString(FlatApplication.getApplication().getContentResolver(),
//            Settings.Secure.ANDROID_ID); //android id

    private WANMsgBean wanMsgBean = new WANMsgBean();
    private Gson gson = new Gson();
    private volatile List<WANMsgBean.MsgStaffInfoUpdate> modifyPersonList = new ArrayList<>();
    private volatile List<WANMsgBean.MsgStaffPhotoUpdate> modifyPersonPhotoList = new ArrayList<>();
    private volatile boolean isPersonSaving = false;
    private volatile boolean isPersonPhotoSaving = false;
    private volatile Future<?> keepAliveFuture, reuploadThread, openTcpFuture;
    private volatile long currentTime;
    public volatile static boolean isTcpConnected = false;
    //    private volatile CopyOnWriteArrayList<Upload> uploadList = new CopyOnWriteArrayList<>();
    private NfcReceiver nfcReceiver;
    private static final String ACTION_NFC_RECEIVER_CARD_NUM = "reciever_nfc";

    public WhiteListService() {
    }

    @Override
    public void OnSaveAndUpload(final long historyId, final Bitmap face, final String card_path,
                                final String face_path, final String com_status, final float score,
                                final String time, final String registerId,
                                final int result, final int compareType) { //在这里保存历史记录，然后上传
        ThreadPoolUtils.execute(() -> {
            try {
                Logger.d("hisID:" + historyId);
                ImgUtils.getUtils().saveBitmap(face, face_path);
                History history = new History();
                history.setId(historyId);
                history.setIc_card(registerId);
                history.setTemplatePhotoPath(card_path);
                history.setFace_path(face_path);
                history.setTime(time);
                history.setCom_status(com_status);
                history.setScore(score);
                history.setResult(result);
                history.setCompareType(compareType);
                history.setInOut(Integer.parseInt(Preference.getDoorway()));  //出入口   1为入口  2为出口

                if (FlatApplication.getApplication().getDaoSession().getHistoryDao().count() >= 10000) {
                    try {
                        if (Preference.getHisFirstIdId() != null && !Preference.getHisFirstIdId().equals("")) {
                            long firstID = Long.parseLong(Preference.getHisFirstIdId());
                            History first = FlatApplication.getApplication().getDaoSession()
                                    .getHistoryDao().load(firstID);
                            FileUtils.deleteFile(first.getFace_path());
                            FileUtils.deleteFile(first.getTemplatePhotoPath());
                            FlatApplication.getApplication().getDaoSession().delete(first);
                            firstID++;
                            Preference.setHisFirstId("" + firstID);
                        }
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
                Preference.setHisLastId("" + historyId);
                FlatApplication.getApplication().getDaoSession().insert(history);
                uploadHistory(history);
                Logger.d("save history = " + history.toString());
//                personCache = null;
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void OnUploadIdentityCard(String[] info) {
    }

    @Override
    public void OnSettingChange(final String setting) {
        Logger.d("与服务器相关的设置 发生改变 = " + setting);
        ThreadPoolUtils.execute(() -> {
            if (setting.contains("name")) {//改设备位置
                uploadConfig();
            } else {//改ip或端口
                closeTcp();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                openTcp();
            }

        });

    }

    private void uploadConfig() {
        WANMsgBean.MsgUpConfig msgUpConfig = wanMsgBean.new MsgUpConfig();
        String site = Preference.getCustomName() == null ? "未设置" : Preference.getCustomName();
        msgUpConfig.setSerial(sn);
        msgUpConfig.setInstallSite(site);
        msgUpConfig.setDeviceName(site);
        msgUpConfig.setDetectScore(Preference.getOvoCompareThreshold() == null
                ? "75" : Preference.getOvoCompareThreshold());
        msgUpConfig.setWLDetectThreshold(Preference.getCompareThreshold() == null
                ? "75" : Preference.getCompareThreshold());
        msgUpConfig.setInOut(Integer.valueOf(Preference.getDoorway()));
        sendMsg(WANMsgConstant.NET_UPLOAD_CONFIG, msgUpConfig);
    }


    private void sendMsg(@WANMsgConstant.ECOMMANDTYPE int cmdType, WANMsgBean.BaseDeailMsg obj) {
        synchronized (this) {
            try {
                WANMsgBean.CmdContent cmdContent = wanMsgBean.new CmdContent();
                cmdContent.setCmdType(cmdType);
                cmdContent.setMsg(obj);
                String str = gson.toJson(cmdContent);
                if (cmdType != 8) {
//                    Logger.d("sendMsg   str= " + str);
                }
                str = "[".concat(str).concat("]");
                JSONArray jsonArray;

                jsonArray = new JSONArray(str);
                SignalRManager.getInstance().sendMessage(jsonArray);
//                OnConnectStatusChangeListener.OnServerChange(true);
            } catch (JSONException e) {
                e.printStackTrace();
//                OnConnectStatusChangeListener.OnServerChange(false);
//                isTcpConnected = false;
            }
        }
    }

    public class WhiteListBinder extends Binder {
        public WhiteListService getService() {
            return WhiteListService.this;
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        SaveAndUpload.setOnSaveAndUploadListener(this);
        OnServerSettingChangeListener.setOnServerSettingChangeListener(this);
        openTcp();
        IntentFilter filter = new IntentFilter(ACTION_NFC_RECEIVER_CARD_NUM);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        nfcReceiver = new NfcReceiver();
        registerReceiver(nfcReceiver, filter);
//        ThreadPoolUtils.execute(this::OpenSerial);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        SaveAndUpload.setOnSaveAndUploadListener(null);
        OnServerSettingChangeListener.setOnServerSettingChangeListener(null);
        closeTcp();
        if (openTcpFuture != null && (!openTcpFuture.isCancelled() || openTcpFuture.isDone())) {
            openTcpFuture.cancel(true);
            Logger.i("reuploadThread   " + openTcpFuture.isCancelled());
            openTcpFuture = null;
        }
        unregisterReceiver(nfcReceiver);
        closeSerialPort();
        return super.onUnbind(intent);
    }

    private void openTcp() {
        if (isTcpConnected) return;
        openTcpFuture = ThreadPoolUtils.submit(() -> {
            try {
                String hubUrl;
                String ip = Preference.getServerIp();
                String portStr = Preference.getServerPort();
                int port;
                if (portStr == null || portStr.equals("")) {
                    port = 8899;
                } else {
                    port = Integer.valueOf(portStr);
                }
                if (ip == null || ip.equals("")) {
                    hubUrl = "http://taisau.group:" + port + "/signalr";
                } else {
                    hubUrl = "http://" + ip + ":" + port + "/signalr";
                }
//                hubUrl = "http://taisau.group:" + port + "/signalr";
                SignalRManager.getInstance().init(
                        hubUrl, WhiteListService.this.getApplicationContext(), listener);
                SignalRManager.getInstance().beginConnect("MyHub");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // TODO: 2018-04-04  标记一下好找
    SignalRStatusListener listener = new SignalRStatusListener() {
        @Override
        public void OnConnect() {
            Logger.d("OnConnect  " + Thread.currentThread());
            isRun = true;
//            Logger.d("serial number = " + sn);
            ThreadPoolUtils.execute(() -> uploadConfig());
        }

        @Override
        public void OnDisConnect() {
            Logger.d("OnDisConnect   " + Thread.currentThread());
//            handler.post(() -> Toast.makeText(WhiteListService.this,
//                    R.string.tips_connect_exception, Toast.LENGTH_SHORT).show());
            ThreadPoolUtils.execute(() -> {
                try {
                    closeTcp();
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                openTcp();
            });
            OnConnectStatusChangeListener.OnServerChange(false);
            isTcpConnected = false;
        }

        @Override
        public void OnReceiver(String s) {
            currentTime = System.currentTimeMillis();
//            Logger.d("OnReceiver  s = " + s);
            s = s.substring(1, s.length() - 1);
//            Logger.d("OnReceiver  s = " + s);
            try {
                JSONObject jsonObject = new JSONObject(s);
                int cmdType = jsonObject.getInt("CmdType");
                String msgString = jsonObject.getString("Msg");
                if (cmdType != 8) {
//                    Logger.d("OnReceiver    cmdType = " + cmdType + ",msgString = " + msgString);
                }
                if (cmdType == WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE) {
                    msgString = msgString.replace("\\", "");
//                    Logger.d("OnReceiver  去掉反斜杠后：msgString = " + msgString);
                }
                handleMsg(MSG_CMD, cmdType, msgString);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            OnConnectStatusChangeListener.OnServerChange(true);
            isTcpConnected = true;
        }

        @Override
        public void OnSend(boolean b, String s) {
            if (!b) {
                Logger.d("OnSend   b = " + b + ",s = " + s);
                OnDisConnect();
            }
            // 2018-04-03 返回false，则重新发送，若断开连接，要保存对象；
        }

        @Override
        public void OnError(@NonNull Exception e) {
            Logger.e("OnError  e = " + e.toString() + ",isTcpConnected = " + isTcpConnected);
            handler.postDelayed(() -> {
                if (isTcpConnected) {
                    OnDisConnect();
                }
            }, 2000);
        }
    };

    /**
     * 根据 消息类型 和 命令类型 处理消息
     */
    private void handleMsg(int msgTypeInt, int cmdTypeInt, String obj) {
        if (msgTypeInt == MSG_CMD) {
//            Logger.d(cmdTypeInt);
            switch (cmdTypeInt) {
                case WANMsgConstant.NET_DOWNLOAD_ROSTER_INFO_UPDATE://更新下发id的人员，不包含图片
                    Logger.d("NET_DOWNLOAD_ROSTER_INFO_UPDATE");
                    WANMsgBean.MsgStaffInfoUpdate msgStaffInfoUpdate = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgStaffInfoUpdate.class);
                    modifyPersonList.add(msgStaffInfoUpdate);
                    if (!isPersonSaving) {
                        isPersonSaving = true;
                        MainModel.runDetect = false;
                        savePerson();
                    }
                    break;
                case WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE://更新人员的图片
                    Logger.d("NET_DOWNLOAD_ROSTER_PHOTO_UPDATE");
                    WANMsgBean.MsgStaffPhotoUpdate msgStaffPhotoUpdate = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgStaffPhotoUpdate.class);
                    modifyPersonPhotoList.add(msgStaffPhotoUpdate);
                    if (!isPersonPhotoSaving) {
                        isPersonPhotoSaving = true;
                        MainModel.runDetect = false;
                        savePersonPhoto();
                    }
                    break;
                case WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_REMOVE://下发员工照片删除
                    Logger.d("NET_DOWNLOAD_ROSTER_PHOTO_REMOVE");
                    WANMsgBean.MsgStaffPhotoRemove msgStaffPhotoRemove = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgStaffPhotoRemove.class);
                    removePersonPhoto(msgStaffPhotoRemove.getUid(), msgStaffPhotoRemove.getPhotoIndex(), msgStaffPhotoRemove.getOperateTime());
                    break;
                case WANMsgConstant.NET_DOWNLOAD_ROSTER_CLEAN://清空下发的人员
                    Logger.d("NET_DOWNLOAD_ROSTER_CLEAN");
                    WANMsgBean.MsgStaffClean msgStaffClean = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgStaffClean.class);
                    cleanPerson(msgStaffClean.getOperateTime());
                    break;
                case WANMsgConstant.NET_DOWNLOAD_ROSTER_REMOVE://移除 下发的id的人员
                    Logger.d("NET_DOWNLOAD_ROSTER_REMOVE");
                    WANMsgBean.MsgStaffRemove msgStaffRemove = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgStaffRemove.class);
                    removePerson(msgStaffRemove.getUids(), msgStaffRemove.getOperateTime());
                    break;
                case WANMsgConstant.NET_UPLOAD_CONFIG_BACK://上线发送sn信息后收到这个回复
                    Logger.d("NET_UPLOAD_CONFIG_BACK");
//                    WANMsgBean.MsgUpConfigBack msgUpConfigBack = MsgManager.getInstance()
//                            .handleMsg(obj, WANMsgBean.MsgUpConfigBack.class);
                    reuploadHistory();
                    startKeepAliveThread();
                    break;
                case WANMsgConstant.NET_KEEPLIVE://心跳包，每隔5秒，设备发给服务器，服务器原样返回
//                    WANMsgBean.MsgKeepLive msgKeepLive = MsgManager.getInstance()
// .handleMsg(obj, WANMsgBean.MsgKeepLive.class);
//                    Logger.v("NET_KEEPLIVE   sn = " + msgKeepLive.getSerial());
                    break;

                case WANMsgConstant.NET_UPLOAD_RECORD_BACK://上传比对记录后，返回
                    Logger.d("NET_UPLOAD_RECORD_BACK");
                    WANMsgBean.MsgManRecordBack msgManRecordBack = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgManRecordBack.class);
                    String uid = msgManRecordBack.getUid();
                    String createTime = msgManRecordBack.getCreateTime();
                    boolean result = msgManRecordBack.isResult();
                    Logger.d("NET_UPLOAD_RECORD_BACK    Uid=" + uid + ",createTime="
                            + createTime + ",Result=" + result);
                    uploadSuccess(uid, createTime);
                    break;
                case WANMsgConstant.NET_DOWNLOAD_CONFIG://下发配置
                    Logger.d("NET_DOWNLOAD_CONFIG");
                    WANMsgBean.MsgDownConfig msgDownConfig = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgDownConfig.class);

                    String deviceName = msgDownConfig.getDeviceName();
                    Preference.setCustomName(deviceName);
                    String detectScore = msgDownConfig.getDetectScore();
                    Preference.setOvoCompareThreshold(detectScore);//人证比对阈值，即一比一比对
                    String doorway = String.valueOf(msgDownConfig.getInOut());//出入口，1为入口 2为出口
                    Preference.setDoorway(doorway);//
//                        String wlDetectThreshold = object.getString("WLDetectThreshold");
//                        Preference.setCompareThreshold(wlDetectThreshold);//自动比对阈值，即刷脸白名单比对
                    uploadConfig();
                    ConfigChangeListener.OnConfigChange(Float.valueOf(detectScore));
                    break;
                case WANMsgConstant.NET_DOWNLOAD_GFS_GROUPIDS:
                    Logger.d("NET_DOWNLOAD_GFS_GROUPIDS");
                    WANMsgBean.MsgGfsGroupIds msgGfsGroupIds = MsgManager.getInstance()
                            .handleMsg(obj, WANMsgBean.MsgGfsGroupIds.class);
                    saveGfsGroupIds(msgGfsGroupIds.getSerial(), msgGfsGroupIds.getGfsGroupIds(), msgGfsGroupIds.getGfsUrl());
                    break;
                case WANMsgConstant.NET_DOWNLOAD_OPENDOOR:
                    Logger.d("NET_DOWNLOAD_OPENDOOR");
                    try {
                        WANMsgBean.MsgDownOpenDoor msgDownOpenDoor = MsgManager.getInstance()
                                .handleMsg(obj, WANMsgBean.MsgDownOpenDoor.class);
                        String channel = msgDownOpenDoor.getChannel();//闸机通道，也就是设备序列号
                        if (channel.equals(sn)) {
                            String prot = "ttyS1";
                            int baudrate = 9600;
                            SerialPort mSerialPort = new SerialPort(
                                    new File("/dev/" + prot), baudrate, 0);
                            Logger.d("  串口打开        " + mSerialPort.toString());
                            OutputStream mOutputStream = mSerialPort.getOutputStream();
                            mOutputStream.write((byte) 0x01);
                            Logger.i("发送成功:0x01");
                            //开一秒，立马发送关闭指令，有人在闸机中间的话，闸机有红外感应，不会夹到人
                            handler.postDelayed(() -> {
                                try {
                                    mOutputStream.write((byte) 0xf1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }, 200);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case WANMsgConstant.NET_DOWNLOAD_CONTROL:
                    Logger.d("NET_DOWNLOAD_CONTROL");
//                    try {
//                    WANMsgBean.MsgDownControl msgDownControl = MsgManager.getInstance()
// .handleMsg(obj, WANMsgBean.MsgDownControl.class);
//                        int controlNum = msgDownControl.getControlNum();//1关机 2重启设备
//                        switch (controlNum) {
//                            case 1:
//                                PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
//                                pManager.reboot(null);//重启
//                                break;
//                            case 2:
//                                //获得ServiceManager类
//                                Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
//
//                                //获得ServiceManager的getService方法
//                                Method getService = ServiceManager.getMethod("getService", java.lang.String.class);
//
//                                //调用getService获取RemoteService
//                                Object oRemoteService = getService.invoke(null, Context.POWER_SERVICE);
//
//                                //获得IPowerManager.Stub类
//                                Class<?> cStub = Class.forName("android.os.IPowerManager$Stub");
//                                //获得asInterface方法
//                                Method asInterface = cStub.getMethod("asInterface", android.os.IBinder.class);
//                                //调用asInterface方法获取IPowerManager对象
//                                Object oIPowerManager = asInterface.invoke(null, oRemoteService);
//                                //获得shutdown()方法
//                                Method shutdown = oIPowerManager.getClass().getMethod("shutdown", boolean.class, boolean.class);
//                                //调用shutdown()方法
//                                shutdown.invoke(oIPowerManager, false, true);
//                                break;
//                        }
//                    } catch (JSONException | NoSuchMethodException | IllegalAccessException
//                            | ClassNotFoundException | InvocationTargetException e) {
//                        e.printStackTrace();
//                    }
                    break;

            }
        }
    }

    /**
     * 保存下发的比对服务器分组数据
     */
    private void saveGfsGroupIds(String sn, String[] gfsGroupIds, String url) {
        WANMsgBean.MsgGfsGroupIdsBack msgGfsGroupIdsBack = wanMsgBean.new MsgGfsGroupIdsBack();
        try {
            msgGfsGroupIdsBack.setSerial(this.sn);
            if (this.sn.equals(sn)) {
                Preference.setGFSGROUPIdS(gfsGroupIds);
                Preference.setCompareServerUrl(url);
                msgGfsGroupIdsBack.setResult(true);
            } else {
                msgGfsGroupIdsBack.setResult(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            msgGfsGroupIdsBack.setResult(false);
        }
        sendMsg(WANMsgConstant.NET_DOWNLOAD_GFS_GROUPIDS_BACK, msgGfsGroupIdsBack);
    }

    /**
     * 开启心跳包
     */
    private void startKeepAliveThread() {
        if (keepAliveFuture == null || (keepAliveFuture.isDone() || keepAliveFuture.isCancelled())) {
            //
            keepAliveFuture = ThreadPoolUtils.submit(() -> {
                while (isRun) {
                    long time = System.currentTimeMillis();
//                    Logger.d("心跳间隔（毫秒）：" + (time - currentTime));
                    if (time - currentTime < 30000) {
                        WANMsgBean.MsgKeepLive msgKeepLive = wanMsgBean.new MsgKeepLive();
                        msgKeepLive.setSerial(sn);
                        sendMsg(WANMsgConstant.NET_KEEPLIVE, msgKeepLive);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Logger.d("心跳包未收到回复，关闭连接，重新连接");
                        closeTcp();
                        ThreadPoolUtils.execute(() -> {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            openTcp();
                        });

                    }
                }
            });
        }
    }

    public void reOpenTcp() {
        ThreadPoolUtils.execute(() -> {
            closeTcp();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            openTcp();
        });
    }

    private void closeTcp() {

        isRun = false;
        if (keepAliveFuture != null && (!keepAliveFuture.isCancelled() || keepAliveFuture.isDone())) {
            keepAliveFuture.cancel(true);
//            Logger.i("keepAliveFuture   " + keepAliveFuture.isCancelled());
            keepAliveFuture = null;
        }
        if (reuploadThread != null && (!reuploadThread.isCancelled() || reuploadThread.isDone())) {
            reuploadThread.cancel(true);
//            Logger.i("reuploadThread   " + reuploadThread.isCancelled());
            reuploadThread = null;
        }
        isTcpConnected = false;
        OnConnectStatusChangeListener.OnServerChange(false);
    }

    private Person person;

    /**
     * 保存人员信息
     */
    private void savePerson() {
        ThreadPoolUtils.execute(() -> {
            String operateTime = null;
            try {
                while (modifyPersonList.size() != 0) {
                    Logger.d("modifyPersonList.size()=" + modifyPersonList.size());
                    WANMsgBean.MsgStaffInfoUpdate msgStaffInfoUpdate = modifyPersonList.get(0);
                    //操作时间，暂时没定义，需要再补
                    operateTime = msgStaffInfoUpdate.getOperateTime();
                    String uid = msgStaffInfoUpdate.getUid();
                    QueryBuilder qb = FlatApplication.getApplication().getDaoSession()
                            .getPersonDao().queryBuilder().where(PersonDao.Properties.Uid.eq(uid));
                    if (qb.list().size() != 0) {
                        Logger.d("下发人员重复，更新  id=" + msgStaffInfoUpdate.getCIc());
                        person = (Person) qb.list().get(0);
                    } else {
                        person = new Person();
                    }
                    person.setIc_card(msgStaffInfoUpdate.getCIc());
                    person.setUid(msgStaffInfoUpdate.getUid());
                    person.setEnterDate(msgStaffInfoUpdate.getEnterDate());//受雇日期
                    person.setLeaveDate(msgStaffInfoUpdate.getLeaveDate());//离职日期
                    person.setSiteBeginDate(msgStaffInfoUpdate.getSiteBeginDate());//地点开始日期
                    person.setSiteEndDate(msgStaffInfoUpdate.getSiteEndDate());//地点结束日期
                    person.setSafetyCardExpiryDate(msgStaffInfoUpdate.getSafetyCardExpiryDate());//安全卡到期日期

                    FlatApplication.getApplication().getDaoSession().getPersonDao().insertOrReplace(person);
                    Logger.d("下发人员，添加成功  id = " + msgStaffInfoUpdate.getCIc());
                    handler.post(() -> Toast.makeText(WhiteListService.this,
                            R.string.person_save_success, Toast.LENGTH_SHORT).show());
                    modifyPersonList.remove(0);
                    modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_INFO_UPDATE,
                            operateTime, true);
                }
            } catch (NullPointerException e) {
                Logger.d("下发人员，添加异常");
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_INFO_UPDATE,
                        operateTime, false);
            } finally {
                isPersonSaving = false;
                MainModel.runDetect = true;
            }
        });
    }

    /**
     * 保存人员图片
     */
    private void savePersonPhoto() {
        ThreadPoolUtils.execute(() -> {
            String operateTime = null;
            try {
                while (modifyPersonPhotoList.size() != 0) {
                    Logger.d("modifyPersonPhotoList.size()=" + modifyPersonPhotoList.size());
                    WANMsgBean.MsgStaffPhotoUpdate msgStaffPhotoUpdate = modifyPersonPhotoList.get(0);
                    operateTime = msgStaffPhotoUpdate.getOperateTime();
                    String uid = msgStaffPhotoUpdate.getUid();
                    int photoIndex = msgStaffPhotoUpdate.getPhotoIndex();
                    Logger.d("下发人员  图片 uid = +" + uid + ",photoIndex = " + photoIndex);
                    int flag; //增加、修改、删除分别为：0、1、2
                    QueryBuilder<Person> qb = FlatApplication.getApplication().getDaoSession()
                            .getPersonDao().queryBuilder().where(PersonDao.Properties.Uid.eq(uid));
                    if (qb.list().size() != 0) {
                        Logger.d("下发人员  图片   人员已存在，更新");
                        person = qb.list().get(0);
                        flag = 1;
                    } else {
                        person = new Person();
                        flag = 0;
                    }

                    Bitmap bitmap = base64ToBitmap(msgStaffPhotoUpdate.getPhoto());
                    File file;
                    person.setUid(msgStaffPhotoUpdate.getUid());
                    person.setIc_card(msgStaffPhotoUpdate.getUid());

                    String path = TEMPLATE_IMG + "/" + DateFormat.format("yyyyMMdd_HHmmss",
                            Calendar.getInstance(Locale.CHINA)) + "_img.jpg";
                    file = new File(path);
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    bitmap = Bitmap.createScaledBitmap(bitmap, 400, 600, false);
                    ImgUtils.getUtils().saveBitmap(bitmap, path);
                    float bitFea[] = ImgUtils.getUtils().getImgFea(bitmap);
                    int errorCount = 0;
                    if (bitFea == null) {
                        while (bitFea == null && errorCount < 3) {
                            bitFea = ImgUtils.getUtils().getImgFea(bitmap);
                            errorCount++;
                            Logger.d("               下发     人员图片，提取特征值失败次数：" + errorCount);
                            Thread.sleep(200);
                        }
                    }
                    if (bitFea != null) {
                        String whiteFea = TEMPLATE_FEA + "/" + DateFormat.format("yyyyMMdd_HHmmss",
                                Calendar.getInstance(Locale.CHINA)) + "_fea.txt";

                        File photoFile = null;
                        //获取到图片编号后，根据编号去删除原有的图片，然后用新的路径替换,特征值路径固定，直接替换
                        switch (photoIndex) {
                            case 0:
                                if (flag == 1) {//增加、修改、删除分别为：0、1、2
                                    if (person.getImg_path() != null) {
                                        photoFile = new File(person.getImg_path());
                                        whiteFea = person.getFea_path();
                                    }
                                }
                                person.setFea_path(whiteFea);
                                person.setImg_path(path);
                                break;
                            case 1:
                                if (flag == 1) {//增加、修改、删除分别为：0、1、2
                                    if (person.getPush_img_path1() != null) {
                                        photoFile = new File(person.getPush_img_path1());
                                        whiteFea = person.getPush_fea_path1();
                                    }
                                }
                                person.setPush_fea_path1(whiteFea);
                                person.setPush_img_path1(path);
                                break;
                            case 2:
                                if (flag == 1) {//增加、修改、删除分别为：0、1、2
                                    if (person.getPush_img_path2() != null) {
                                        photoFile = new File(person.getPush_img_path2());
                                        whiteFea = person.getPush_fea_path2();
                                    }
                                }
                                person.setPush_fea_path2(whiteFea);
                                person.setPush_img_path2(path);
                                break;
                            case 3:
                                if (flag == 1) {//增加、修改、删除分别为：0、1、2
                                    if (person.getPush_img_path3() != null) {
                                        photoFile = new File(person.getPush_img_path3());
                                        whiteFea = person.getPush_fea_path3();
                                    }
                                }
                                person.setPush_fea_path3(whiteFea);
                                person.setPush_img_path3(path);
                                break;
                            default:
                                if (flag == 1) {//增加、修改、删除分别为：0、1、2
                                    if (person.getImg_path() != null) {
                                        photoFile = new File(person.getImg_path());
                                        whiteFea = person.getFea_path();
                                    }
                                }
                                person.setFea_path(whiteFea);
                                person.setImg_path(path);
                                break;
                        }
                        if (photoFile != null && photoFile.exists()) {
                            Logger.d("下发人员  图片 删除 photoFile = " + photoFile.getAbsolutePath()
                                    + ",删除状态：" + photoFile.delete());
                        }
                        FeaUtils.saveFea(whiteFea, bitFea);
                        FlatApplication.getApplication().getDaoSession().getPersonDao().insertOrReplace(person);
                        Logger.d("下发    人员图片，添加成功");
                        List<Person> changList = new ArrayList<>();
                        changList.add(person);
                        FlatApplication.getApplication().getDaoSession().getPersonDao().insertOrReplaceInTx(changList);
                        handler.post(() -> Toast.makeText(WhiteListService.this,
                                R.string.person_save_success, Toast.LENGTH_SHORT).show());
                        modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE, operateTime, true);
                    } else {
                        file.delete();
                        handler.post(() -> {
//                            Toast.makeText(WhiteListService.this, " 添加ID：" + person.getIc_card() + " 失败。" +
//                                    "\n失败原因：无法提取模版图片特征值，请确认模版图片有效", Toast.LENGTH_LONG).show();
                            handler.post(() -> Toast.makeText(WhiteListService.this,
                                    R.string.person_save_fail, Toast.LENGTH_SHORT).show());
                            Logger.e("下发人员图片，添加失败");
                        });
                        modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE, operateTime, false);
                    }
                    modifyPersonPhotoList.remove(0);
                }
            } catch (IOException | NullPointerException e) {
                Logger.e("下发人员图片，添加异常");
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE, operateTime, false);
            } catch (InterruptedException e) {
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_UPDATE, operateTime, false);
            } finally {
                isPersonPhotoSaving = false;
                MainModel.runDetect = true;
            }
        });
    }

    /**
     * 移除指定的人员图片
     */
    private void removePersonPhoto(String uid, int photoIndex, String operateTime) {
        ThreadPoolUtils.execute(() -> {
            try {
                QueryBuilder<Person> qb = FlatApplication.getApplication().getDaoSession().getPersonDao().queryBuilder()
                        .where((PersonDao.Properties.Uid).eq(uid));
                List<Person> list = qb.list();
                Person mPerson;
                int size = list.size();
                switch (size) {
                    case 0:
                        Logger.e("removePersonPhoto    size==0");
                        modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_REMOVE, operateTime, false);
                        break;
                    case 1:
                        Logger.d("removePersonPhoto    size==1");
                    default:
                        if (size > 1) {
                            Logger.e("removePersonPhoto    size>1");
                        }
                        mPerson = list.get(0);
                        mPerson.setIndexImgPath(photoIndex, null);
                        mPerson.setIndexFeaPath(photoIndex, null);
                        String imgPath = mPerson.getIndexImgPath(photoIndex);
                        String feaPath = mPerson.getIndexFeaPath(photoIndex);
                        FileUtils.deleteFile(imgPath);
                        FileUtils.deleteFile(feaPath);
                        FlatApplication.getApplication().getDaoSession().getPersonDao().insertOrReplace(mPerson);
                }
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_REMOVE, operateTime, true);
            } catch (Exception e) {
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_PHOTO_REMOVE, operateTime, false);
            }
        });
    }

    private void cleanPerson(String operateTime) {
        ThreadPoolUtils.execute(() -> {
            try {
                List<Person> list = FlatApplication.getApplication().getDaoSession()
                        .getPersonDao().loadAll();
                Logger.d("下发删除全部   人员成功");
                for (Person person : list) {
                    FileUtils.deleteFile(person.getAllFilePath());
                }
                FlatApplication.getApplication().getDaoSession().getPersonDao().deleteInTx(list);
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_CLEAN, operateTime, true);
            } catch (Exception e) {
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_CLEAN, operateTime, false);
            }
        });
    }

    private void removePerson(final String[] uids, String operateTime) {
        ThreadPoolUtils.execute(() -> {
            try {
//                QueryBuilder<Person> qb = FlatApplication.getApplication().getDaoSession().getPersonDao().queryBuilder()
//                        .where((PersonDao.Properties.Uid).eq(uids));
                QueryBuilder<Person> qb = FlatApplication.getApplication().getDaoSession().getPersonDao().queryBuilder()
                        .where((PersonDao.Properties.Uid).in((Object[]) uids));
                List<Person> list = qb.list();
                Logger.d("list.size()=" + list.size());
                if (list.size() != 0) {
                    FlatApplication.getApplication().getDaoSession().getPersonDao().deleteInTx(list);
                }
                for (Person person : list) {
                    FileUtils.deleteFile(person.getAllFilePath());
                }
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_REMOVE, operateTime, true);
            } catch (Exception e) {
                e.printStackTrace();
                modifyPersonMsgBack(WANMsgConstant.NET_DOWNLOAD_ROSTER_REMOVE, operateTime, false);
            }
        });
    }

    /**
     * 对人员操作后，需要反馈
     */
    private void modifyPersonMsgBack(int msgType, String operateTime, boolean isSuccessful) {
        Logger.i("modifyPersonMsgBack      msgType= " + msgType);
        WANMsgBean.MsgStaffOperateBack msgStaffOperateBack = wanMsgBean.new MsgStaffOperateBack();
        msgStaffOperateBack.setSerial(sn);//设备序列号
        msgStaffOperateBack.setOperateTime(operateTime);//操作时间(格式"yyyy-MM-dd HH:mm:ss.fff")
        msgStaffOperateBack.setOperateType(msgType);//操作类型（名单信息更新、名单照片更新、名单删除、名单清空）
        msgStaffOperateBack.setSucceed(isSuccessful);//是否成功
        sendMsg(WANMsgConstant.NET_DOWNLOAD_ROSTER_OPERATE_BACK, msgStaffOperateBack);
    }

    private void uploadHistory(final History history) {
        Bitmap templateBitmap = BitmapFactory.decodeFile(history.getTemplatePhotoPath());
        String templatePhoto = bitmapToBase64(templateBitmap);
        Bitmap siteBitmap = BitmapFactory.decodeFile(history.getFace_path());
        String sidePhoto = bitmapToBase64(siteBitmap);
        WANMsgBean.MsgManRecord msgManRecord = wanMsgBean.new MsgManRecord();
        msgManRecord.setUid(history.getIc_card());
        msgManRecord.setCIc(history.getIc_card());
        msgManRecord.setCompareType(history.getCompareType());
        msgManRecord.setSerial(sn);
        msgManRecord.setCreateTime(history.getTime());
        msgManRecord.setDScore(String.valueOf(history.getScore()));
        msgManRecord.setDRes(history.getResult());
        msgManRecord.setTemplateImg(templatePhoto);
        msgManRecord.setSiteImg(sidePhoto);
        msgManRecord.setInOut(history.getInOut());
        msgManRecord.setUpdateState(1);//1实时数据，2历史数据
        sendMsg(WANMsgConstant.NET_UPLOAD_RECORD, msgManRecord);
    }

    private void reuploadHistory() {
        if (reuploadThread == null || (reuploadThread.isDone() || reuploadThread.isCancelled())) {
            reuploadThread = ThreadPoolUtils.submit(() -> {
                List<History> reuploadHistoryList = FlatApplication.getApplication()
                        .getDaoSession().getHistoryDao().queryBuilder()
                        .where(HistoryDao.Properties.Upload_status.eq(false)).list();
                for (History history : reuploadHistoryList) {
                    Bitmap templateBitmap = BitmapFactory.decodeFile(history.getTemplatePhotoPath());
                    String templatePhoto = bitmapToBase64(templateBitmap);
                    Bitmap siteBitmap = BitmapFactory.decodeFile(history.getFace_path());
                    String sidePhoto = bitmapToBase64(siteBitmap);
                    WANMsgBean.MsgManRecord msgManRecord = wanMsgBean.new MsgManRecord();
                    msgManRecord.setUid(history.getIc_card());
                    msgManRecord.setCIc(history.getIc_card());
                    msgManRecord.setCompareType(history.getCompareType());
                    msgManRecord.setSerial(sn);
                    msgManRecord.setCreateTime(history.getTime());
                    msgManRecord.setDScore(String.valueOf(history.getScore()));
                    msgManRecord.setDRes(history.getResult());
                    msgManRecord.setTemplateImg(templatePhoto);
                    msgManRecord.setSiteImg(sidePhoto);
                    msgManRecord.setInOut(history.getInOut());
                    msgManRecord.setUpdateState(2);//1实时数据，2历史数据
                    sendMsg(WANMsgConstant.NET_UPLOAD_RECORD, msgManRecord);
                }
            });
        }

    }

    private void uploadSuccess(String uid, String createTime) {
        //2018-01-20 16:13:43
        try {
            List<History> histories = FlatApplication.getApplication().getDaoSession()
                    .getHistoryDao().queryBuilder()
                    .where(HistoryDao.Properties.Time.eq(createTime),
                            HistoryDao.Properties.Ic_card.eq(uid)).list();
            if (histories.size() == 1) {
                histories.get(0).setUpload_status(true);
                FlatApplication.getApplication().getDaoSession().getHistoryDao().update(histories.get(0));
                Logger.d("上传历史记录成功，设置记录上传状态为 true  " +
                        " 比对记录的 比对时间为 = " + histories.get(0).getTime());
                List<Person> persons = FlatApplication.getApplication().getDaoSession()
                        .getPersonDao().queryBuilder()
                        .where(PersonDao.Properties.Uid.eq(histories.get(0).getIc_card())).list();
                if (persons.size() != 0) {
                    for (Person p : persons) {
                        if (p.getPerson_type() == 2) {
                            FileUtils.deleteFile(p.getImg_path());
                        }
                    }
                }
            } else if (histories.size() == 0) {
                Logger.e("上传历史记录成功，查询uid发现       0     个结果");
            } else {
                Logger.e("上传历史记录成功，查询uid发现      多     个结果   histories.size = " + histories.size());
                for (History h : histories) {
                    Logger.e(h.toString());
                }
                histories.get(0).setUpload_status(true);
                FlatApplication.getApplication().getDaoSession().getHistoryDao().update(histories.get(0));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * byte数组中取int数值，本方法适用于(低位在前，高位在后)的顺序，和 @Code{intToBytes()}配套使用
     *
     * @param src    byte数组
     * @param offset 从数组的第offset位开始
     * @return int数值
     */
    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = ((src[offset] & 0xFF)
                | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    public byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }

    /**
     * base64ToBitmap
     */
    public static Bitmap base64ToBitmap(String base64String) {
        byte[] decode = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decode, 0, decode.length);
    }

    /**
     * bitmap转为base64
     */
    public String bitmapToBase64(Bitmap bitmap) {
        String result = null;
        ByteArrayOutputStream baos = null;
        try {
            if (bitmap != null) {
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                baos.flush();
                baos.close();
                byte[] bitmapBytes = baos.toByteArray();
                result = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    //java 合并两个byte数组
    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }


    // 判断字节数组前几位是否符合一定规则
    public static boolean isHeadMatch(byte[] data, byte[] pattern) {
        if (data == null || data.length < pattern.length)
            return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i])
                return false;
        }
        return true;
    }

    // 判断字节数组后几位是否符合一定规则
    public static boolean isTailMatch(byte[] data, byte[] pattern) {
        if (data == null || data.length < pattern.length)
            return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[data.length - pattern.length + i] != pattern[i])
                return false;
        }
        return true;
    }

    private static OnCardDetectListener onCardDetectListener;

    public static void setOnCardDetectListener(OnCardDetectListener listener) {
        onCardDetectListener = listener;
    }

    public static Person personCache;

    private class NfcReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_NFC_RECEIVER_CARD_NUM.equals(action)) {
                byte[] extraId = intent.getByteArrayExtra("nfc_id");
                Logger.d("NfcReceiver  extraId = " + Arrays.toString(extraId));//[-77, -36, -43, -7]
                StringBuilder id = new StringBuilder();
                for (byte b : extraId) {
                    String hex = Integer.toHexString((int) b & 0xff);
                    if (hex.length() == 1) {
                        hex = '0' + hex;
                    }
                    id.append(hex);
                    Logger.d("NfcReceiver  id = " + id.toString());//[-77, -36, -43, -7]
                }
                handleCardNum(id.toString().toUpperCase());//9F11A2C
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {//判断其中一个就可以了
                Logger.d("USB已经连接！");
                ThreadPoolUtils.execute(() -> {
                    try {
                        Thread.sleep(2000);
                        String path = XmlUtil.getPath();
                        if (path != null) {
                            List<String> config = XmlUtil.readXml(path);
                            if (config.size() != 0) {
                                if (!config.get(1).equals(Preference.getServerIp())
                                        || !config.get(2).equals(Preference.getServerPort())) {
                                    Preference.setCustomName(config.get(0));
                                    Preference.setServerIp(config.get(1));
                                    Preference.setServerPort(config.get(2));
                                    Preference.setDoorway(config.get(3));
                                    Preference.setAliveCheck(config.get(4));
                                    OnSettingChange(config.get(1));
                                    Logger.d("U盘导入，ip或端口改变");
                                } else if (!config.get(0).equals(Preference.getCustomName())
                                        || !config.get(3).equals(Preference.getDoorway())
                                        || !config.get(4).equals(Preference.getAliveCheck())) {
                                    Preference.setCustomName(config.get(0));
                                    Preference.setDoorway(config.get(3));
                                    Preference.setAliveCheck(config.get(4));
                                    OnSettingChange("name");
                                    Logger.d("U盘导入，ip端口不变，改了配置");
                                }
                                Logger.d("config.get(4) = " + config.get(4));
                                Logger.d("Preference.getAliveCheck = " + Preference.getAliveCheck());
                                ConfigChangeListener.OnConfigChange(Boolean.parseBoolean(config.get(4)));
                                handler.post(() ->
                                        Toast.makeText(FlatApplication.getApplication(), "配置文件讀取成功", Toast.LENGTH_LONG).show()
                                );
                                Logger.e("配置文件讀取成功");
                            } else {
                                handler.post(() ->
                                        Toast.makeText(FlatApplication.getApplication(), "U盘讀取配置文件異常", Toast.LENGTH_LONG).show()
                                );
                                Logger.e("U盘读取配置文件异常");
//                            XmlUtil.saveXml(path, "测试", "192.168.2.133", 1);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {//USB被拔出
                Logger.d("USB连接断开！");

            }
        }
    }

    private void handleCardNum(String id) {
        Logger.d("id=" + id);
        QueryBuilder<Person> builder = FlatApplication.getApplication().getDaoSession().getPersonDao().queryBuilder()
                .where(PersonDao.Properties.Uid.eq(id));
        final List<Person> personList = builder.list();
        Logger.d("personList.size = " + personList);
        handler.post(() -> {
            switch (personList.size()) {
                case 0:
                    Toast.makeText(FlatApplication.getApplication(), R.string.person_no_import, Toast.LENGTH_LONG).show();
                    return;
                case 1:
                    personCache = personList.get(0);
                    if (!Constant.isDoubleScreen) {
                        Intent i = new Intent(WhiteListService.this, MainActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        WhiteListService.this.startActivity(i);
                    }
//                    if (onCardDetectListener != null) {
//                        onCardDetectListener.onDetectCard(personList.get(0));
//                    }
                    break;
                default:
                    Toast.makeText(FlatApplication.getApplication(), R.string.multiple_person, Toast.LENGTH_LONG).show();
            }
        });
    }

    private SerialPort mSerialPort;
    protected InputStream mInputStream;
    protected OutputStream mOutputStream;
    private boolean isTestRun;

    private void OpenSerial() {
        // 打开
        try {
            String prot = "ttyS1";
            int baudrate = 9600;
            mSerialPort = new SerialPort(new File("/dev/" + prot), baudrate, 0);
            Logger.d("  串口打开        " + mSerialPort.toString());
            mInputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();
//            serialPortStartRead();
        } catch (SecurityException | IOException e) {
            Logger.d("  串口打开失败");
            e.printStackTrace();
            try {
                Thread.sleep(10000);
//                OpenSerial();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
    }

    private List<Byte> messageList = new ArrayList<>();

    private void serialPortStartRead() {
        // 接收
        isTestRun = true;
        while (isTestRun) {
            int size;
            try {
                byte[] buffer = new byte[1];
                if (mInputStream == null) {
                    Thread.sleep(10000);
//                    OpenSerial();
                    return;
                } else {
                    mInputStream = mSerialPort.getInputStream();
                }
                size = mInputStream.read(buffer);
                if (size > 0) {
                    messageList.add(buffer[0]);
//                    Logger.d("串口 接收  信息 =" + Arrays.toString(messageList.toArray()));
                    if (messageList.size() == 4) {
//                        Logger.d("4 byte 卡号 =" + Arrays.toString(messageList.toArray()));//[-97, 17, -94, 12]
                        StringBuilder id = new StringBuilder();
                        for (byte b : messageList) {
                            id.append(Integer.toHexString((int) b & 0xff));
                        }
                        handleCardNum(id.toString().toUpperCase());//9F11A2C
                        messageList.clear();
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendSerialPortMsg() {
        // 发送
//        ThreadPoolUtils.execute(() -> {
        try {
            if (mSerialPort == null) {
//                ThreadPoolUtils.execute(this::OpenSerial);
            } else {
                //01开门
                //f1关门
                mOutputStream.write((byte) 0x01);
                Logger.i("发送成功:0x01");
                //开一秒，立马发送关闭指令，有人在闸机中间的话，闸机有红外感应，不会夹到人
//            Thread.sleep(200);
                handler.postDelayed(() -> {
                    try {
                        mOutputStream.write((byte) 0xf1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, 200);
//            mOutputStream.write((byte) 0xf1);
                Logger.i("发送成功:0xf1 ");
            }
        } catch (Exception e) {
            Logger.i("发送失败");
            e.printStackTrace();
        }
//        });
    }

    public void sendSerialPortMsgClose() {
        // 发送
        ThreadPoolUtils.execute(() -> {
            try {
                if (mSerialPort == null) {
//                    OpenSerial();
                }
                //01开门
                //f1关门
//                mOutputStream.write((byte) 0x01);
//                Logger.i("发送成功:0x01");
//                //开一秒，立马发送关闭指令，有人在闸机中间的话，闸机有红外感应，不会夹到人
//                Thread.sleep(1000);
                mOutputStream.write((byte) 0xf1);
                Logger.i("发送成功:0xf1 ");
            } catch (Exception e) {
                Logger.i("发送失败");
                e.printStackTrace();
            }
        });
    }

    private void closeSerialPort() {
        isTestRun = false;
        if (mSerialPort != null) {
            try {
                Logger.d("close serial port");
                mSerialPort.close();
                mSerialPort = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        messageList.clear();
    }

}
