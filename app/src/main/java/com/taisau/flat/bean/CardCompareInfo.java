package com.taisau.flat.bean;

/**
 * Created by whx on 2018-03-02
 */

public class CardCompareInfo {
    /*{"json1":{"img":"/9j/4ASkZ．．"},"json2":{"img":"/9j/4ASkZ．．"}}*/
    /**
     * json1 : {"img":"/9j/4ASkZ．．"}
     * json2 : {"img":"/9j/4ASkZ．．"}
     */

    private Json1Bean json1;
    private Json2Bean json2;

    public Json1Bean getJson1() {
        return json1;
    }

    public void setJson1(Json1Bean json1) {
        this.json1 = json1;
    }

    public Json2Bean getJson2() {
        return json2;
    }

    public void setJson2(Json2Bean json2) {
        this.json2 = json2;
    }

    public static class Json1Bean {
        /**
         * img : /9j/4ASkZ．．
         */

        private String img;

        public String getImg() {
            return img;
        }

        public void setImg(String img) {
            this.img = img;
        }
    }

    public static class Json2Bean {
        /**
         * img : /9j/4ASkZ．．
         */

        private String img;

        public String getImg() {
            return img;
        }

        public void setImg(String img) {
            this.img = img;
        }
    }

}
