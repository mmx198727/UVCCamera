package com.usbcamera.android.bean;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FormatsBean implements Serializable {

    private List<FormatBean> formats;

    public List<FormatBean> getFormats() { return formats; }
    public void setFormats(List<FormatBean> formats) { this.formats = formats; }

    /**
     * 获取视频格式列表
     * @return
     */
    public List<String> getFormatStrList(){
        List<String> list = new ArrayList<>();

        for (FormatBean format:formats) {
            list.add(format.toString());
        }

        return list;
    }

    /**
     * 获取分辨率列表
     * 需要指定视频格式
     * @param formatId  视频格式编号
     * @return
     */
    public List<String> getSizeStrList(int formatId){
        List<String> list = new ArrayList<>();

        for (SizeBean size:formats.get(formatId).getSizeList()) {
            list.add(size.toString());
        }

        return list;
    }

    /**
     * 获取帧率列表
     * 需要指定视频格式和分辨率
     * @param formatId  视频格式编号
     * @param sizeId    分辨率编号
     * @return
     */
    public List<String> getFpsStrList(int formatId, int sizeId){
        List<String> list = new ArrayList<>();

        for (FpsBean fps:formats.get(formatId).getSizeList().get(sizeId).getFpsList()) {
            list.add(fps.toString());
        }

        return list;
    }

    /**
     * 通过Json字符串创建视频格式列表对象
     * @param jsonStr
     * @return
     */
    public static FormatsBean convertFromJsonStr(String jsonStr){
        Gson gson = new Gson();
        //解析成我们的根实体类
        return  gson.fromJson(jsonStr, FormatsBean.class);
    }

    /**
     * 测试分析Json字符串
     * @param jsonData
     */
    private static void parseJSONWithJSONObject(String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            JSONArray jsonArray = json.getJSONArray("formats");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String index = jsonObject.getString("index");
                String type = jsonObject.getString("type");
                String guidFormat = jsonObject.getString("guidFormat");
                String sizeList = jsonObject.getString("sizeList");


                JSONArray sizeArray = jsonObject.getJSONArray("size");
                for (int m = 0; m < sizeArray.length(); m++) {
                    JSONObject value = sizeArray.getJSONObject(m);
                    String width = value.getString("width");
                    String height = value.getString("height");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}




