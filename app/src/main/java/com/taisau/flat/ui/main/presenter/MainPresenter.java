package com.taisau.flat.ui.main.presenter;

import android.graphics.Bitmap;
import android.hardware.Camera;

import com.taisau.flat.listener.OnCardDetectListener;
import com.taisau.flat.ui.main.contract.MainContract;
import com.taisau.flat.ui.main.model.MainModel;


/**
 * Created by Administrator on 2017-09-07
 */

public class MainPresenter implements MainContract.Presenter {
    private MainContract.View view;
    private MainContract.Model model;
//    private Context mContext;

    public MainPresenter(/*Context context,*/ MainContract.View view) {
        this.view = view;
//        this.mContext = context;
        model = new MainModel(/*mContext,*/this);

    }

    public  Camera.PreviewCallback getPreviewCallback(){
        return model.getPreviewCallback();
    }
    public OnCardDetectListener getCardDetectListener(){return model.getCardDetectListener();}

    public void updateAdsTitle(){
        try {
            view.updateAdsTitle(model.getAdsTitle());
        } catch (Exception e) {
            e.printStackTrace();
            view.updateAdsTitle("标题");
        }
    }
    public void updateAdsSubitle(){
        try {
            view.updateAdsSubtitle(model.getAdsSubtitle());
        } catch (Exception e) {
            e.printStackTrace();
            view.updateAdsTitle("副标题");
        }
    }
    public void updateAdsPath(){
        try {
            //包含默认、用户设置和服务器下发
            view.updateAdsImage(model.getAdsImagePath());//可能多图
        } catch (Exception e) {
            e.printStackTrace();
            view.updateAdsTitle(null);
        }
    }
    public void updateUserName(){
        try {
            //包含默认、用户设置和服务器下发
            view.updateUserName(model.getUserName());//可能多图
        } catch (Exception e) {
            e.printStackTrace();
            view.updateUserName("公安局");
        }
    }



    public void initTime(){
        model.startUpdateTime();
    }
    public void stopTime(){
        model.stopUpdateTime();
    }

    @Override
    public void updateTimeToView(String time){
        view.updateTimeStatus(time);
    }
    @Override
    public void showToast(String msg){
        view.showToastMsg(msg);
    }

    @Override
    public void updateFaceStruct(/*GFace.FacePointInfo*/String info, int pic_width, int pic_height) {
        view.updateFaceStruct(info,pic_width,pic_height);
    }

    @Override
    public void updateFaceFrame(long[] position, int pic_width, int pic_height) {
        view.updateFaceFrame(position,pic_width,pic_height);
    }

    @Override
    public void setCompareLayoutVisibility(int visitable) {
        view.setCompareLayoutVisibility(visitable);
    }

    @Override
    public void updateCompareRealRes(Bitmap real) {
        view.updateCompareRealRes(real);
    }

    @Override
    public void updateCompareCardRes(Bitmap card) {
        view.updateCompareCardRes(card);
    }

    @Override
    public void updateCompareResultInfo(String result, int textColor) {
        view.updateCompareResultInfo(result,textColor);
    }

    @Override
    public void updateCompareResultScore(String result, int visitable){//对比分值，分值大于65显示绿色
        view.updateCompareResultScore(result,visitable);
    }

    @Override
    public void updateCompareResultImg(int resId, int visitable) {
        view.updateCompareResultImg(resId,visitable);
    }

    @Override
    public void updateSoundStatus(boolean isInit,int soundNum) {
        model.changeSound(isInit,soundNum);
        view.updateSoundStatus(soundNum);
    }

    @Override
    public void setRunDetect(boolean run) {
        model.setRunDetect(run);
    }
}
