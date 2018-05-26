package com.taisau.flat.bean;

/**
 * Created by whx on 2018-02-26
 */

public class CardCompareResult {
    /*{msg=操作成功, score=100.0, success=true}*/
    private boolean success;
    private String msg;
    private float score;


    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "CardCompareResult{" +
                "success=" + success +
                ", msg='" + msg + '\'' +
                ", score=" + score +
                '}';
    }
}
