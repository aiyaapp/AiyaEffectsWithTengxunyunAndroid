package com.tencent.liteav.demo.videoupload.impl;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频上传参数
 */
public class TVCUploadInfo {
    private String fileType;
    private String filePath;
    private String coverType;
    private String coverPath;


    private String fileName = null;
    private long fileSize = 0;
    private String coverName;
    private boolean isShouldRetry = false;

    /**
     * 创建上传参数
     * @param fileType  文件类型
     * @param filePath  文件本地路径
     * @param coverType 封面图片类型
     * @param coverPath 封面图片本地路径
     */
    public TVCUploadInfo(String fileType, String filePath, String coverType, String coverPath){
        this.fileType = fileType;
        this.filePath = filePath;
        this.coverType = coverType;
        this.coverPath = coverPath;
    }

    public String getFileType() {
        return fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getCoverImgType() {
        return coverType;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public boolean isNeedCover(){
        return !TextUtils.isEmpty(coverType) && !TextUtils.isEmpty(coverPath);
    }

    public String getFileName(){
        if (null == fileName) {
            int pos = filePath.lastIndexOf('/');
            if (-1 == pos) {
                pos = 0;
            } else {
                pos++;
            }
            fileName = filePath.substring(pos);
        }

        return fileName;
    }
    
    public String getCoverName(){
        if (null == coverName) {
            int pos = coverPath.lastIndexOf('/');
            if (-1 == pos) {
                pos = 0;
            } else {
                pos++;
            }
            coverName = coverPath.substring(pos);
        }
        return coverName;
    }

    public long getFileSize() {
//        if (0 == fileSize){
//            TXCLog.i("getFileSize", "getFileSize: "+filePath);
//            File file = new File(filePath);
//            try {
//                if (file.exists()) {
//                    FileInputStream fis = new FileInputStream(file);
//                    fileSize = fis.available();
//                }
//            }catch (Exception e){
//                TXCLog.e("getFileSize", "getFileSize: "+e);
//            }
//        }
        return fileSize;
    }

    public boolean getIsShouldRetry() {
        return isShouldRetry;
    }

    public void setIsShouldRetry(boolean isShouldRetry) {
        this.isShouldRetry = isShouldRetry;
    }

    public boolean isContainSpecialCharacters(String string){
        String regEx = "[/ : * ? \" < >]";
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(string);
        return matcher.find();
    }
}
