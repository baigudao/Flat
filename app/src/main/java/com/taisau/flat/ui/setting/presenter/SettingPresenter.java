package com.taisau.flat.ui.setting.presenter;

import com.taisau.flat.FlatApplication;
import com.taisau.flat.ui.setting.model.ISettingModel;
import com.taisau.flat.ui.setting.model.SettingModel;
import com.taisau.flat.ui.setting.view.ISettingView;

/**
 * Created by whx on 2017-08-14
 */

public class SettingPresenter {

    private ISettingView view;
    private ISettingModel model;

    private boolean hasNew=false;
    public String version="";

    String msg="";

    public SettingPresenter(ISettingView view){
        this.view=view;
        model=new SettingModel(this);
    }

    public void checkVersion(final String version){
        this.version=version;

    }

    boolean compareVersion(String net, String loc){
        String[] netSplit=net.split("\\.");
        String[] locSplit=loc.split("\\.");
        int locV = 0,netV = 0;
        for (int i=0;i<4;i++)
        {
            locV= locV+(int) (Integer.parseInt(locSplit[i])* Math.pow(10,3-i));
            netV=netV+(int) (Integer.parseInt(netSplit[i])* Math.pow(10,3-i));
            if (locV<netV)
                return true;
        }
        return false;

    }

    public void clearData(){
        model.clearDevice();
    }

    public void clearDataComplete() {
        view.clearDataComplete();
        if (FlatApplication.whiteListService != null) {
            FlatApplication.whiteListService.reOpenTcp();
        }
    }

}
