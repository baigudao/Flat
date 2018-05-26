package com.taisau.flat.ui.main.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Toast;

import com.GFaceNew;
import com.orhanobut.logger.Logger;
import com.taisau.flat.FlatApplication;
import com.taisau.flat.R;
import com.taisau.flat.bean.CardCompareInfo;
import com.taisau.flat.bean.Person;
import com.taisau.flat.http.NetClient;
import com.taisau.flat.listener.ConfigChangeListener;
import com.taisau.flat.listener.OnCardDetectListener;
import com.taisau.flat.listener.SaveAndUpload;
import com.taisau.flat.service.WhiteListService;
import com.taisau.flat.ui.main.contract.MainContract;
import com.taisau.flat.ui.main.utils.FeaAction;
import com.taisau.flat.util.FeaUtils;
import com.taisau.flat.util.FileUtils;
import com.taisau.flat.util.ImgUtils;
import com.taisau.flat.util.Preference;
import com.taisau.flat.util.SoundUtils;
import com.taisau.flat.util.ThreadPoolUtils;
import com.taisau.flat.util.YUVUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static com.taisau.flat.FlatApplication.getApplication;
import static com.taisau.flat.util.Constant.FACE_IMG;

//import java.text.DateFormat;


/**
 * Created by whx on 2017-09-04
 */

public class MainModel implements MainContract.Model, Camera.PreviewCallback, OnCardDetectListener
        , ConfigChangeListener.OnServerConfigChangeListener {

    private StringBuffer time = new StringBuffer();
    private MainContract.Presenter presenter;
    private static boolean isRun = false;
    private static int noFaceCount = 0;
    //是否进行人脸比对
    public static volatile boolean runDetect = false;
    //检测到人脸
    private static boolean hasFace = false;
    //是否进行人脸比对
    private static boolean runCompare = false;

    //    private float[] modFea;
    private List<float[]> modFeasList;

    //历史信息Model
    private static long historyID;

    private volatile float comScore = 75;

    private static int noFace = 200;

    private volatile int disCount = 0;

    private FeaAction feaAction = new FeaAction();

    private volatile boolean cpuSleeping = false;

    private Handler handler = new Handler();


    private Thread updateTimeThread;

    private volatile boolean isSlow = false;
    private volatile long testStartTime = 0, testRequestStartTime = 0;
    private Person mPerson;
    private boolean isReceive = false; //是否接收到后端比对服务器的返回信息
    private float score;
    private ArrayList<Rect> mFaceRectList;//人脸矩形坐标
    private volatile boolean aliveCheck;

    public MainModel(/*Context context,*/ MainContract.Presenter presenter2) {
        this.presenter = presenter2;
    }

    public Camera.PreviewCallback getPreviewCallback() {
        return MainModel.this;
    }

    //    @Override
    public OnCardDetectListener getCardDetectListener() {
        return MainModel.this;
    }


    @Override
    public void changeSound(boolean isInit, int soundNum) {
        int current = SoundUtils.getInstance().getCurrentVolume(AudioManager.STREAM_MUSIC);
        switch (soundNum) {
            case 0: //静音
                SoundUtils.getInstance().muteSound(AudioManager.STREAM_MUSIC);
                Preference.setDevVolume("0");
                break;
            case -1: //音量 +1
                SoundUtils.getInstance().addSound(AudioManager.STREAM_MUSIC);
                current += 1;
                Preference.setDevVolume(String.valueOf(current));
                break;
            case -2: //音量 -1
                SoundUtils.getInstance().decreaseSound(AudioManager.STREAM_MUSIC);
                current -= 1;
                Preference.setDevVolume(String.valueOf(current));
                break;
            case -3: //音量最大
                SoundUtils.getInstance().maxSound(AudioManager.STREAM_MUSIC);
                int max = SoundUtils.getInstance().getMaxVolume(AudioManager.STREAM_MUSIC);
                Preference.setDevVolume(String.valueOf(max));
                break;
            default: //直接设置音量
                if (isInit) {
                    SoundUtils.getInstance().setSoundMute(AudioManager.STREAM_MUSIC, soundNum);
                } else {
                    SoundUtils.getInstance().setSound(AudioManager.STREAM_MUSIC, soundNum);
                }
                Preference.setDevVolume(String.valueOf(soundNum));
                break;
        }

    }

    @Override
    public void setRunDetect(boolean run) {
        runDetect = run;
        presenter.updateFaceFrame(changeSituation(0, 0, 0, 0), 640, 360);
    }

    private void initTime() {
        isRun = true;
        runDetect = true;
//        CompareService.setOnCardDetectListener(this);
        WhiteListService.setOnCardDetectListener(this);
        initSetting();
//        if (WC) {
//            initWhiteFea();
//        }
        //  2017/09/07 星期四 15:27:50
        //Logger.e(TAG, "onResume: 年 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.YEAR) );//2017
        // Logger.e(TAG, "onResume: 月 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.MONTH) );//8
        //Logger.e(TAG, "onResume: 日 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.DAY_OF_MONTH) );//7
        // Logger.e(TAG, "onResume: 星期 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.DAY_OF_WEEK) );//5
        // Logger.e(TAG, "onResume: 日期 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.DATE) );//7
        // Logger.e(TAG, "onResume: 时 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.HOUR) );//3
        // Logger.e(TAG, "onResume: 分 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.MINUTE) );//27
        //Logger.e(TAG, "onResume: 秒 = "+Calendar.getInstance(Locale.CHINA).get(Calendar.SECOND) );//50
//        updateTimeThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    synchronized (this) {
//                        while (isRun) {
//                            time.delete(0, time.length());
//
//                            Date curDate = new Date(System.currentTimeMillis());
//                            switch (curDate.getDay()) {
//                                case 1:
//                                    time.append("星期一 ");
//                                    break;
//                                case 2:
//                                    time.append("星期二 ");
//                                    break;
//                                case 3:
//                                    time.append("星期三 ");
//                                    break;
//                                case 4:
//                                    time.append("星期四 ");
//                                    break;
//                                case 5:
//                                    time.append("星期五 ");
//                                    break;
//                                case 6:
//                                    time.append("星期六 ");
//                                    break;
//                                case 0:
//                                    time.append("星期日 ");
//                                    break;
//                            }
//                            time.append(curDate.getHours()).append(":");
//                            if (curDate.getMinutes() < 10)
//                                time.append("0").append(curDate.getMinutes());
//                            else
//                                time.append(curDate.getMinutes());
//
//                            handler.post(() -> presenter.updateTimeToView(time.toString()));
//                            //等待
//                            wait((60 - curDate.getSeconds()) * 1000);
//                        }
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    //页面退出后需要中断线程
//                }
//            }
//        });
//        updateTimeThread.start();
    }

    private void stopTime() {
        isRun = false;
        runDetect = false;
        stopThread(updateTimeThread);
        ConfigChangeListener.setOnServerConfigChangeListener(null);
    }

    private void stopThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    private void initSetting() {
        if (Preference.getHisLastId() != null && !Preference.getHisLastId().equals(""))
            historyID = Long.parseLong(Preference.getHisLastId()) + 1;
        else
            historyID = 1;
        comScore = Preference.getOvoCompareThreshold() == null ?
                75 : Float.parseFloat(Preference.getOvoCompareThreshold());
//        if (Preference.getNoFaceCount() != null && !Preference.getNoFaceCount().equals("")) {
//            noFace = Integer.parseInt(Preference.getNoFaceCount());
//        }
        if (Preference.getAliveCheck() != null && !Preference.getAliveCheck().equals("")) {
            try {
                aliveCheck = Boolean.parseBoolean(Preference.getAliveCheck());
            } catch (Exception e) {
                e.printStackTrace();
                aliveCheck = false;
            }
        }
//        FlatApplication.whiteListService.sendSerialPortMsgClose();//比对通过发送串口命令开启闸机
        ConfigChangeListener.setOnServerConfigChangeListener(this);
    }


    @Override
    public String getAdsTitle() {
        return Preference.getMainTittle();
    }

    @Override
    public String getAdsSubtitle() {
        return Preference.getSubTittle();
    }

    @Override
    public String[] getAdsImagePath() {
        // /mnt/internal_sd/DCIM/Camera/IMG_20170907_131825.jpg
        // /mnt/internal_sd/DCIM/Camera/IMG_20170907_110757.jpg
        return new String[]{Preference.getAdsPath()/*,
        "/mnt/internal_sd/DCIM/Camera/IMG_20170907_131825.jpg",
        "/mnt/internal_sd/DCIM/Camera/IMG_20170907_110757.jpg"*/
        };
    }

    @Override
    public String getUserName() {
        return Preference.getCustomName();
    }


    @Override
    public void startUpdateTime() {
        initTime();
        if (WhiteListService.personCache != null) {
            onDetectCard(WhiteListService.personCache);
            WhiteListService.personCache = null;
        }
    }

    @Override
    public void stopUpdateTime() {
        WhiteListService.setOnCardDetectListener(null);
        stopTime();
    }


    private void clearMode() {
        noFaceCount = 0;
        presenter.setCompareLayoutVisibility(View.GONE);
        presenter.updateCompareResultScore("", View.GONE);
        presenter.updateCompareResultImg(-1, View.GONE);
        runCompare = false;
    }

    private String icCard;
    private String cardPath;

    private boolean isAfterNow(String dateStr) {
        if (dateStr.equals("null") || dateStr.equals("")) {
            return false;
        }
        Date now = new Date();
//        java.text.DateFormat dateFormat = SimpleDateFormat.getInstance();
//        dateFormat.
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

        try {
            Date date = simpleDateFormat.parse(dateStr);
            return date.after(now);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isBeforeNow(String dateStr) {
        if (dateStr.equals("null") || dateStr.equals("")) {
            return false;
        }
        Date now = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
//        java.text.DateFormat dateFormat = SimpleDateFormat.getInstance();
        try {
            Date date = simpleDateFormat.parse(dateStr);
            return date.before(now);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onDetectCard(Person person) {//接收到IC卡的id,从数据库中取特征值
        testStartTime = System.currentTimeMillis();
        mPerson = person;
        Observable.create((ObservableOnSubscribe<Person>) e -> {
//            Date enterDate = dateFormat.parse(person.getEnterDate());//受雇日期
//            Date leaveDate = dateFormat.parse(person.getLeaveDate());//离职日期
//            Date siteBeginDate = dateFormat.parse(person.getSiteBeginDate());//地点开始日期
//            Date siteEndDate = dateFormat.parse(person.getSiteEndDate());//地点结束日期
//            Date safetyCardExpiryDate = dateFormat.parse(person.getSafetyCardExpiryDate());//安全卡到期日期
            if (isAfterNow(person.getEnterDate()) || isAfterNow(person.getSiteBeginDate())
                    || isBeforeNow(person.getSafetyCardExpiryDate())
                    || isBeforeNow(person.getLeaveDate())
                    || isBeforeNow(person.getSiteEndDate())) {
                e.onComplete();
            } else {
                e.onNext(person);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(person1 -> {

                            setRunDetect(true);

//                            Logger.e("准备处理  耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
                            isSlow = false;

                            presenter.updateCompareResultInfo("", Color.WHITE);

                            icCard = person.getIc_card();
                            cardPath = person.getImg_path();
                            Bitmap cardBit = BitmapFactory.decodeFile(cardPath);
                            if (cardPath == null || cardBit == null) {
                                Toast.makeText(getApplication(),
                                        getApplication().getResources().
                                                getString(R.string.template_picture_is_null),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                cardBit = Bitmap.createScaledBitmap(cardBit, 200, 300, false);
                                presenter.updateCompareRealRes(null);
                                presenter.updateCompareCardRes(cardBit);//左边为人脸模板照
                                presenter.updateCompareResultImg(-1, View.GONE);
                                presenter.updateCompareResultScore("start", View.GONE);

                                presenter.setCompareLayoutVisibility(View.VISIBLE);
                                noFaceCount = 0;

                                modFeasList = FeaUtils.getFeaList(person.getAllFeaPath());
                                presenter.updateCompareResultInfo(getApplication().getString(R.string.take_picture_tip), Color.WHITE);
                                Logger.e("处理IC卡信息  耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
//                                handler.postDelayed(() -> runCompare = true, 20);
                                runCompare = true;
                            }
                        }, Throwable::printStackTrace
                        , () -> Toast.makeText(getApplication(),
                                getApplication().getResources().
                                        getString(R.string.out_of_range), Toast.LENGTH_SHORT).show());
    }

    private long[] situation = new long[4];

    private long[] changeSituation(long... situations) {
//        System.arraycopy(situations, 0, situation, 0, situations.length);
        situation[0] = situations[0];
        situation[1] = situations[1];
        situation[2] = situations[2];
        situation[3] = situations[3];
        return situation;
    }

    @Override
    public void onPreviewFrame(final byte[] data, Camera camera) {
        if (runDetect) {
            if (isSlow) {
                if (!cpuSleeping) {
                    cpuSleeping = true;
                } else {
                    return;
                }
                ThreadPoolUtils.execute(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (this) {
                            try {
                                wait(700);
                                cpuSleeping = false;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            //检测人脸
            mFaceRectList = GFaceNew.checkFaceCameraPreview(data, 1280, 720, (short) 90);
//            Logger.v("mFaceRectList = " + mFaceRectList);
            if (mFaceRectList != null) {
                hasFace = true;// left：198,top：572,right：610,bottom：984          调整之后left：298,调整之后top：242,调整之后right：1110,调整之后bottom：654
                Logger.e("left：" + mFaceRectList.get(0).left + ",top：" + mFaceRectList.get(0).top + ",right：" + mFaceRectList.get(0).right + ",bottom：" + mFaceRectList.get(0).bottom);
                mFaceRectList = adjustFaceInfo(mFaceRectList, true);//mFaceRectList = [Rect(200, 428 - 648, 876)]
                Logger.e("调整之后left：" + mFaceRectList.get(0).left + ",调整之后top：" + mFaceRectList.get(0).top + ",调整之后right：" + mFaceRectList.get(0).right + ",调整之后bottom：" + mFaceRectList.get(0).bottom);
                //检测到人脸后，生成人脸框
                presenter.updateFaceFrame(changeSituation(mFaceRectList.get(0).left, mFaceRectList.get(0).top,
                        mFaceRectList.get(0).right, mFaceRectList.get(0).bottom), 1280, 720);
                isSlow = false;
            } else {
                hasFace = false;
                presenter.updateFaceFrame(changeSituation(0, 0, 0, 0), 1280, 720);
                if (noFaceCount < noFace) {
                    noFaceCount++;
                } else {
                    clearMode();
                    isSlow = true;
                }
            }
        }
        if (hasFace && runCompare) {
            if (mFaceRectList.size() > 1) {
                // TODO: 2018-04-18 提示人数多于一人，不进行比对
                presenter.showToast("檢測到多人，不進行比對");
                return;
            }
            Logger.v("mFaceRectList.size  = " + mFaceRectList.size() + ",getLivebody    1 aliveCheck = " + aliveCheck);
            if (aliveCheck) {
                if (GFaceNew.getLivebody(data, 1280, 720)) {
                    Logger.e("活体   比对前的   耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
                    frontCardCompare(data);
                } else {
                    Logger.v("getLivebody              false ");
                }
            } else {
                Logger.e("不活体  比对前的   耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
                frontCardCompare(data);
            }
        }
    }

    /**
     * 后端比对，接口调通但这个项目不需要，留着其他项目可以参考
     */
    private void rearCardCompare(byte[] data) {
        runCompare = false;
        noFaceCount = 0;
        Bitmap faceBit = YUVUtils.yuv2Bitmap(data, 1280, 720);
        Bitmap scaleFaceBit = ImgUtils.cropBitmapWithRect(faceBit, mFaceRectList.get(0));
        presenter.updateCompareRealRes(scaleFaceBit);// 剪裁顯示
        String date = DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date()).toString();
        String facePath = FACE_IMG + "/" + DateFormat.format("yyyyMMdd_HH_mm_ss",
                Calendar.getInstance(Locale.CHINA)).toString() + "_face_img.jpg";
//        ImgUtils.getUtils().saveBitmap(scaleFaceBit, facePath);
        Logger.e("请求后台比对前的 准备 耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
        testRequestStartTime = System.currentTimeMillis();
        Bitmap cardBit = BitmapFactory.decodeFile(mPerson.getImg_path());
        CardCompareInfo cardCompareInfo = new CardCompareInfo();
        CardCompareInfo.Json1Bean json1 = new CardCompareInfo.Json1Bean();
        json1.setImg(ImgUtils.bitmapToBase64(cardBit));
        CardCompareInfo.Json2Bean json2 = new CardCompareInfo.Json2Bean();
        json2.setImg(ImgUtils.bitmapToBase64(faceBit));
        cardCompareInfo.setJson1(json1);
        cardCompareInfo.setJson2(json2);
        final Bitmap finalFaceBit = faceBit;
        NetClient.getInstance().getCompareAPI().cardCompare(
                Preference.getCompareServerUrl() + "/gface/face/comparison_two_face",
                cardCompareInfo)
                .observeOn(Schedulers.io())
                .doFinally(() -> {
                    if (isReceive) {
                        isReceive = false;
                    }
                    Logger.d("getCompareAPI cardCompare   doFinally");
                })
                .subscribeOn(Schedulers.io())
                .subscribe(cardCompareResult -> {
                    Logger.e("网络  耗时(毫秒)：" + (System.currentTimeMillis() - testRequestStartTime));
                    isReceive = true;
                    if (cardCompareResult != null) {

                        Logger.d("getCompareAPI cardCompare   CardCompareResult = " + cardCompareResult.toString());
                        if (cardCompareResult.isSuccess()) {
                            score = cardCompareResult.getScore();
                            Logger.d("score=" + score);
                            handler.post(() -> {//比对处理
                                handleCompareResult(mPerson.getIc_card(), finalFaceBit, date, facePath, mPerson.getImg_path(),
                                        score, 2);
                            });

                        } else {
                            score = 0.02f;
                            Logger.d("getCompareAPI cardCompare   not success");
                            handler.post(() -> Toast.makeText(getApplication(),
                                    "后台比对返回失败", Toast.LENGTH_LONG).show());
                            compare(faceBit, scaleFaceBit, date, facePath);
                        }
                    }
                }, throwable -> {
                    Logger.d("throwable:" + throwable);
                    Logger.d("throwable:" + throwable.getCause());
                    handler.post(() -> Toast.makeText(getApplication(), "网络异常，无法后台比对", Toast.LENGTH_LONG).show());
                    compare(faceBit, scaleFaceBit, date, facePath);
                });
    }

    //        ImageUtils.save(faceBit1, new File("/storage/emulated/0/DCIM/Camera/test.jpg"), Bitmap.CompressFormat.JPEG);
    private void frontCardCompare(byte[] data) {
        runCompare = false;
        noFaceCount = 0;
        Bitmap faceBit = YUVUtils.yuv2Bitmap(data, 1280, 720);
        ImgUtils.getUtils().saveBitmap(faceBit, "/storage/emulated/0/DCIM/Camera/test.jpg");

        Bitmap faceBit1 = ImgUtils.rotate(faceBit, 270, 640, 360, false);
        ImgUtils.getUtils().saveBitmap(faceBit1, "/storage/emulated/0/DCIM/Camera/test_rotate.jpg");

        Bitmap scaleFaceBit = ImgUtils.cropBitmapWithRect(faceBit1, mFaceRectList.get(0));
        ImgUtils.getUtils().saveBitmap(scaleFaceBit, "/storage/emulated/0/DCIM/Camera/test_scale.jpg");

        presenter.updateCompareRealRes(scaleFaceBit);// 剪裁顯示
        String date = DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date()).toString();
        String facePath = FACE_IMG + "/" + DateFormat.format("yyyyMMdd_HH_mm_ss", Calendar.getInstance(Locale.CHINA)).toString() + "_face_img.png";
        compare(faceBit1, scaleFaceBit, date, facePath);
    }

    private void compare(Bitmap faceBit, Bitmap scaleFaceBit, String date, String facePath) {
        Logger.e("提取现场特征值  开始 耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
//        Bitmap var1 = BitmapFactory.decodeFile("/storage/emulated/0/DCIM/Camera/IMG_20180517_105030.jpg");
        float[] com_fea = GFaceNew.getFeature(faceBit);
        Logger.e("提取现场特征值  结束 耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
        if (!faceBit.isRecycled()) {
            faceBit.recycle();
        }
        float score = 0;
        if (com_fea != null) {
            float temp;
            for (float[] fea : modFeasList) {
                temp = GFaceNew.compareFeature(fea, com_fea);
                if (temp > score) {
                    score = temp;
                }
            }
        } else {
            Logger.e("提取现场特征值   为空！");
        }
        score = (float) (Math.round(score * 100)) / 100;
        Logger.e("score=" + score);
        presenter.updateCompareResultInfo(FlatApplication.getApplication()
                .getResources().getString(R.string.ic_num) + Long.parseLong(FileUtils.reverse(icCard), 16), Color.WHITE);
        // TODO: 2018-04-16
        handleCompareResult(icCard, scaleFaceBit, date, facePath, cardPath, score, 2);
    }

    private void handleCompareResult(String icCard, Bitmap faceBit, String date, String facePath,
                                     String templatePhotoPath, float score, int compareType) {
        String mistakeValues;
//        boolean isBlack = person.getPerson_type() == 1;//白名单为0，黑名单为1
        int result = 1;
        if (score > comScore) {
            result = 0;
            String score1 = getApplication().getResources().getString(R.string.score_instance);
            String score2 = String.format(score1, score);
            presenter.updateCompareResultScore(score2, View.VISIBLE);
            presenter.updateCompareResultImg(R.mipmap.compare_pass, View.VISIBLE);
            mistakeValues = getApplication().getString(R.string.compare_pass_score) + score;
//            FlatApplication.whiteListService.sendSerialPortMsg();//比对通过发送串口命令开启闸机
            Logger.e("比对通过！");
        } else {
            mistakeValues = getApplication().getString(R.string.compare_fail_score) + score;
            presenter.updateCompareResultImg(R.mipmap.compare_fail_error_man, View.VISIBLE);
            presenter.updateCompareResultScore(getApplication().getString(R.string.person_ic_no_accord), View.VISIBLE);
        }
        Logger.e("比对  耗时(毫秒)：" + (System.currentTimeMillis() - testStartTime));
        SaveAndUpload.OnSaveAndUpload(historyID, faceBit, templatePhotoPath, facePath, mistakeValues,
                score, date, icCard, result, compareType);
        historyID++;
        disCount++;
        handler.postDelayed(() -> {
            if (disCount == 1) {
                presenter.updateCompareCardRes(null);
                presenter.updateCompareRealRes(null);
                presenter.updateCompareResultScore("", View.GONE);
                presenter.updateCompareResultImg(-1, View.GONE);
                presenter.updateCompareResultInfo("", View.VISIBLE);
                presenter.setCompareLayoutVisibility(View.GONE);
            }
            disCount--;
        }, 3000);
    }

    private ArrayList<Rect> adjustFaceInfo(ArrayList<Rect> mFaceRectList, boolean isAdd) {
        if (isAdd) {
            mFaceRectList.get(0).left = mFaceRectList.get(0).left + 100;//越大，框越往左
            mFaceRectList.get(0).right = mFaceRectList.get(0).right + 500;
            mFaceRectList.get(0).top = mFaceRectList.get(0).top - 330;//越大，框越往下
            mFaceRectList.get(0).bottom = mFaceRectList.get(0).bottom - 330;
        } else {
            mFaceRectList.get(0).left = mFaceRectList.get(0).left - 200;
            mFaceRectList.get(0).right = mFaceRectList.get(0).right - 500;
            mFaceRectList.get(0).top = mFaceRectList.get(0).top + 330;
            mFaceRectList.get(0).bottom = mFaceRectList.get(0).bottom + 330;
        }
        return mFaceRectList;
    }

    @Override
    public void OnConfigChange(float setting) {
        Logger.d("OnConfigChange    setting = " + setting);
        comScore = setting;
    }

    @Override
    public void OnConfigChange(boolean openAliveCheck) {
        Logger.d("OnConfigChange    openAliveCheck = " + openAliveCheck);
        aliveCheck = openAliveCheck;
    }
}
