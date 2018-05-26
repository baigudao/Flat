package com.taisau.flat;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import com.GFaceNew;
import com.blankj.utilcode.util.Utils;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;
import com.squareup.leakcanary.LeakCanary;
import com.taisau.flat.bean.DaoMaster;
import com.taisau.flat.bean.DaoSession;
import com.taisau.flat.service.WhiteListService;
import com.taisau.flat.util.CrashHandler;
import com.taisau.flat.util.Preference;
import com.taisau.flat.util.ThreadPoolUtils;

import org.greenrobot.greendao.database.Database;

import java.io.File;
import java.io.FileFilter;

import static com.taisau.flat.util.Constant.FACE_IMG;
import static com.taisau.flat.util.Constant.LIB_DIR;
import static com.taisau.flat.util.Constant.TEMPLATE_FEA;
import static com.taisau.flat.util.Constant.TEMPLATE_IMG;


/**
 * Created by whx on 2018/02/11
 */

public class FlatApplication extends Application {
    private static FlatApplication application;

    private DaoSession daoSession;
    public static WhiteListService whiteListService;

    public DaoSession getDaoSession() {
        return daoSession;
    }


    public static FlatApplication getApplication() {
        return application;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        //初始化GFaceNew
        authorizationInit();

        File fileLibDir = new File(LIB_DIR);
//        File fileFace6 = new File(FILE_FACE6);
//        File fileFace7 = new File(FILE_FACE7);
        File templateFea = new File(TEMPLATE_FEA);
        File faceImg = new File(FACE_IMG);
        File templateImg = new File(TEMPLATE_IMG);
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, "faceFlat");
        Database db = helper.getWritableDb();
        daoSession = new DaoMaster(db).newSession();
        if (!fileLibDir.exists())
            fileLibDir.mkdir();
//        if (!fileFace6.exists())
//            fileFace6.mkdir();
//        if (!fileFace7.exists())
//            fileFace7.mkdir();
        if (!templateFea.exists())
            templateFea.mkdir();
        if (!faceImg.exists())
            faceImg.mkdir();
        if (!templateImg.exists())
            templateImg.mkdir();
        if (getDaoSession().getHistoryDao().count() == 0)
            Preference.setHisFirstId("1");

        if (!BuildConfig.DEBUG) {
            CrashHandler crashHandler = CrashHandler.getInstance();
            crashHandler.init(getApplicationContext());
        }
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
        bindService();

        FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                .showThreadInfo(true)  // (Optional) Whether to show thread info or not. Default true
                .methodCount(0)         // (Optional) How many method line to show. Default 2
                .methodOffset(7)        // (Optional) Hides internal method calls up to offset. Default 5
//                .logStrategy(customLog) // (Optional) Changes the log strategy to print out. Default LogCat
                .tag("[[[ whx ]]]")   // (Optional) Global tag for every log. Default PRETTY_LOGGER
                .build();

        Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy) {
            @Override
            public boolean isLoggable(int priority, String tag) {
                return BuildConfig.DEBUG;
            }
        });

//        Logger.d("getNumberOfCPUCores = "+getNumberOfCPUCores());//4
//        ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(4);
//        scheduledThreadPool.schedule(runnable对象, 2000, TimeUnit.MILLISECONDS);
        ThreadPoolUtils.init(getNumberOfCPUCores() * 2);//线程池初始化

        // init it in the function of onCreate in ur Application
        Utils.init(application);
    }

    private void authorizationInit() {
        GFaceNew.initRecognizer(this);
        GFaceNew.initLvingbody(this);
    }

    public void bindService() {
        bindService(new Intent(this, WhiteListService.class), whiteListConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection whiteListConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            whiteListService = ((WhiteListService.WhiteListBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            whiteListService = null;
        }
    };


    @Override
    public void onTerminate() {
        super.onTerminate();
//        shouldRun = false;
        unbindService(whiteListConnection);
    }

    public static int getNumberOfCPUCores() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
            // Gingerbread doesn't support giving a single application access to both cores, but a
            // handful of devices (Atrix 4G and Droid X2 for example) were released with a dual-core
            // chipset and Gingerbread; that can let an app in the background run without impacting
            // the foreground application. But for our purposes, it makes them single core.
            return 1;  //上面的意思就是2.3以前不支持多核,有些特殊的设备有双核...不考虑,就当单核!!
        }
        int cores;
        try {
            cores = new File("/sys/devices/system/cpu/").listFiles(CPU_FILTER).length;
        } catch (SecurityException | NullPointerException e) {
            cores = 0;   //这个常量得自己约定
        }
        return cores;
    }

    private static final FileFilter CPU_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            String path = pathname.getName();
            //regex is slow, so checking char by char.
            if (path.startsWith("cpu")) {
                for (int i = 3; i < path.length(); i++) {
                    if (path.charAt(i) < '0' || path.charAt(i) > '9') {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    };
}
