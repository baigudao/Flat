package com.taisau.flat.http;

import com.taisau.flat.bean.CardCompareInfo;
import com.taisau.flat.bean.CardCompareResult;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;




/**
 * Created by whx on 2018/1/30
 */

public interface CompareAPI {

//    /**
//     * 外接的java比对后台，一比多
//     */
//    @POST
//    Observable<FaceCompareResult> faceCompare(@Url String server, @Body FaceCompareInfo json);

    /**
     * 外接的java比对后台，一比一
     */
    @POST
    Observable<CardCompareResult> cardCompare(@Url String server, @Body CardCompareInfo json);
}
