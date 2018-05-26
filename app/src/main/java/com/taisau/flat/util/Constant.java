package com.taisau.flat.util;

import android.os.Environment;

/**
 * Created by Administrator on 2017/2/22 0022.
 */

public interface Constant {
    String LIB_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/taisau_flat";
    String FILE_FACE6 = LIB_DIR + "/face_GFace6";
    String FILE_FACE7 = LIB_DIR + "/face_GFace7";

    String TEMPLATE_FEA = LIB_DIR + "/template_fea";
    String TEMPLATE_IMG = LIB_DIR + "/template_img";
    String FACE_IMG = LIB_DIR + "/face_img";
    String ADS_IMG = LIB_DIR + "/ads_img";
    boolean isDoubleScreen = false;
    //    String FACE_FEA=LIB_DIR+"/face_fea";
    //    String WHITE_IMG=LIB_DIR+"/white_img";
    //    String WHITE_FEA=LIB_DIR+"/white_fea";
    //    String SOFT_NAME="FaceCompare_Android";

    /**
     * true：表示前端比对，也即本地算法比对；
     * false：表示后端比对，也即传输图片给java后台去比对
     */
    boolean isFrontCompare = true;
}
