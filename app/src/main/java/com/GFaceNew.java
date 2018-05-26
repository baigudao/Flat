package com;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Log;

import com.mv.face.auth.Config;
import com.mv.face.livebody.Livingbody;
import com.mv.face.recognize.RecognizedFace;
import com.mv.face.recognize.Recognizer;
import com.mv.face.recognize.RecognizerWrapper;
import com.mv.face.recognize.TraceResult;

import java.util.ArrayList;


/**
 * Created by Administrator on 2018/3/31.
 */

public class GFaceNew {
    private static String TAG="___WW___GFaceNew";

    private static boolean bDebug =true;
    private static Config mConfig;
    private static RecognizerWrapper mRecognizerWrapper;
    private static Livingbody mLivingbody;
    public  static Recognizer mRecognizer = null;

    private static final String AUTH_DIR = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Face/Auth";


    public static void initRecognizer(Context context) {

        Log.d(TAG, "initRecognizer");

        mConfig = new Config();
        mRecognizerWrapper = RecognizerWrapper.instance();


        // 初始化识别引擎
        int result = mRecognizerWrapper.init(context, mConfig);
        mRecognizer = mRecognizerWrapper.getRecognizer();

        Log.e(TAG, "onCreate: init recognizer result = " + result);
    }

    /**
     *
     * @param context 上下文
     * @return          1       成功
     *                  -1      人脸检测器模型文件不存在。
     *                  -2      landmark 模型文件不存在
     *                  -3      livebody 模型文件不存在
     *                  -4      licence 文件不存在
     *                  -5      invalid licence
     *                  -6      初始化 recognizer 时 jni 发生异常
     */

    public static int initLvingbody(Context context){
        mLivingbody = new Livingbody();
        int result = mLivingbody.init(context, mConfig);

        Log.e(TAG, "initLvingbody: init result -->" + result);

        return result;
    }


    //以下函数必须在initRecognizer函数调用后才能使用

    /*
    public static Recognizer getRecognizer(){

        return mRecognizer;
    }
    */


    public static void printfLog(String strMsg){
        if(bDebug){
            Log.e(TAG, strMsg);
        }
    }

    public static boolean testLivebody(byte[] frame, int width, int height) {
        short orientation = Livingbody.Orientation.UP_TURNED;
        long start = System.currentTimeMillis();
        int result = mLivingbody.identifyCameraPreview(frame, width, height, orientation);
        printfLog("testLivebody: takes time --> " + (System.currentTimeMillis() - start) + "ms");
        printfLog("testLivebody: " + result);

        return  true;
    }

    public static boolean getLivebody(byte[] frame, int width, int height) {
        short orientation = Livingbody.Orientation.RIGHT_TURNED;
        int result = mLivingbody.identifyCameraPreview(frame, width, height, orientation);

        if(result == 1){
            return true;
        }

        return false;
    }

    public static ArrayList<Rect> checkFace(Bitmap var1){
        return mRecognizer.checkFace(var1);
    }

    public static ArrayList<Rect> checkFaceCameraPreview(byte[] var1, int var2, int var3, short var4){
        return mRecognizer.checkFaceCameraPreview(var1,var2,var3,var4);
    }

    public static float[] getFeature(Bitmap var1){
        return mRecognizer.getFeature(var1);
    }

    public static float[] getFeatureWithRect(Bitmap var1, Rect var2){
        return mRecognizer.getFeatureWithRect(var1,var2);
    }

    public static float[] getFeatureWithRectCameraPreview(byte[] var1, int var2, int var3, short var4, Rect var5){
        return mRecognizer.getFeatureWithRectCameraPreview(var1, var2,var3, var4, var5);
    }

    public static RecognizedFace recognize(Bitmap var1){
        return mRecognizer.recognize(var1);
    }

    public static RecognizedFace recognizeCameraPreview(byte[] var1, int var2, int var3, short var4){
        return mRecognizer.recognizeCameraPreview(var1,var2,var3,var4);
    }

    public static ArrayList<RecognizedFace> recognizeWithRect(Bitmap var1, Rect var2, int var3){
        return mRecognizer.recognizeWithRect(var1,var2,var3);
    }

    public static ArrayList<RecognizedFace> recognizeWithRectCameraPreview(byte[] var1, int var2, int var3, short var4, Rect var5, int var6){
        return mRecognizer.recognizeWithRectCameraPreview(var1,var2,var3,var4, var5, var6);
    }

    public static float compareFeature(float[] var1, float[] var2){
        return mRecognizer.compareFeature(var1,var2);
    }

    public static ArrayList<RecognizedFace> recognizeFeature(float[] var1, int var2){
        return mRecognizer.recognizeFeature(var1, var2);
    }

    public synchronized static int addFeature(String var1, float[] var2){
        return mRecognizer.addFeature(var1, var2);
    }

    public synchronized static int deleteFeature(String var1){
        return mRecognizer.deleteFeature(var1);
    }

    public synchronized static int saveFeatures(){
        return mRecognizer.saveFeatures();
    }

    public static int cleanAllFeatures(){
        return mRecognizer.cleanAllFeatures();
    }

    public static int getFeatureLibSize(){
        return mRecognizer.getFeatureLibSize();
    }

    public static TraceResult trace(byte[] var1, int var2, int var3, short var4){
        return mRecognizer.trace(var1,var2,var3,var4);
    }


    }
