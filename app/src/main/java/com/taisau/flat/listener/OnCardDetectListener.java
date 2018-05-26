package com.taisau.flat.listener;


import com.taisau.flat.bean.Person;

/**
 * Created by Administrator on 2016/9/6 0006.
 */
public interface OnCardDetectListener {
    void onDetectCard(/*String icCardId*/Person person);
}
