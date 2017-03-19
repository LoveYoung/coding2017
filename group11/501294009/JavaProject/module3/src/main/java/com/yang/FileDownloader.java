package com.yang;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.*;
import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Dev_yang on 2017/3/16.
 *
 *
 * multi thread download
 */
public class FileDownloader {
    Logger logger= LoggerFactory.getLogger(FileDownloader.class);
    private String url;
    private String destinationPath;

    private  AtomicInteger recevivedLength=new AtomicInteger(0);
    private String  fileName;
    private int threadNum;
    /**
     * optional
     */
    private FileListener fileListener;

    private List<Thread> threadList=new LinkedList<>();
    private RandomAccessFile randomAccessFile;
    private String fileFullPath;
    private AtomicInteger failThreadNum=new AtomicInteger(0);
    private String downloadResult;
    private Proxy proxy;


    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public void setListener(FileListener fileListener) {
        this.fileListener = fileListener;
    }


    private void downloadFail(String message) {
        downloadResult = message;
        failThreadNum.incrementAndGet();
        logger.info("download fail ! error info=> "+message);
    }

    /**
     *
     * @param destinationPath
     * @param fileName give a null mean use original fileName,only provide a new fileName
     * @param url  download url
     * @param threadNum the number of thread to download file at the same time
     *                  ,number limit between  1 and 10;
     */
    public FileDownloader(String url, String destinationPath, String fileName, int threadNum) {
        paramCheck(url, destinationPath, threadNum);
        this.url = url;
        this.destinationPath = destinationPath;
        if(StringUtils.isEmpty(fileName)){
            fileName=  FilenameUtils.getBaseName(url);
        }
        this.fileName = fileName;
        this.threadNum = threadNum;
    }


    public FileDownloader(String url, String destinationPath, int threadNum) {
       this(url,destinationPath,null,threadNum);
    }

    private void paramCheck(String url, String destinationPath, int threadNum) {
        if(StringUtils.isEmpty(url)||StringUtils.isEmpty(destinationPath)){

            throw new RuntimeException("string can't empty or null");
        }


        if(threadNum<1||threadNum>10){
            throw new RuntimeException("the value of thread number wrong,this value must between  1 and 10 ");
        }
    }

    /**
     *

     */

    public void startDownload() throws IOException {
        if(fileListener!=null){
            fileListener.beforeDownload();
        }



        URL url=new URL(this.url);
        URLConnection connection = url.openConnection();
        int fileLength = connection.getContentLength();//todo think about different condition this fileLength value;
        logger.info("file size is "+convertFileSize(fileLength));
        int fileSegmentLength = fileLength / threadNum;



        fileFullPath = new StringBuilder(this.destinationPath).
                append(File.separator).
                append(fileName).
                append(".").
                append(FilenameUtils.getExtension(this.url)).
                toString();

        File file = new File(fileFullPath);
        if(file.exists()){
           FileUtils.forceDelete(file);
        }
        logger.info("fileFullPath："+fileFullPath);
        randomAccessFile = new RandomAccessFile(fileFullPath, "rw");
        randomAccessFile.setLength(fileLength);
        int start=0;
        int end=start+fileSegmentLength;
        for (int i = 0; i < threadNum; i++) {
            if(failThreadNum.get()>0){
                break;
            }
            startDownloadTask(start,end);
            start=end;
            if(i==threadNum-1){
                end=fileLength;
            }else{
                end=start+fileSegmentLength;
            }
        }


        Set<Thread> finishedThread=new LinkedHashSet<>(threadNum);
        DecimalFormat    df   = new DecimalFormat("######0.00");
        while (finishedThread.size()<threadNum&&failThreadNum.get()<1){

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            logger.info("file has download "+df.format(recevivedLength.get()*100.0/fileLength)+"%");
            synchronized (threadList){
                threadList.forEach(t->{
                    if(!t.isAlive()){
                        finishedThread.add(t);
                    }
                });
            }


        }

        logger.info("file has download "+df.format(recevivedLength.get()*100.0/fileLength)+"%");

        if(failThreadNum.get()>0){
            try {
             file=   new File(fileFullPath);
                if(file.exists()){
                    randomAccessFile.close();//todo if no this step ,file is opening then next step will fail
                    FileUtils.forceDelete(file);
                }

            } catch (IOException e) {
                logger.error(" delete file fail ! error info :"+e.getMessage());
            }
        }


        logger.info("file download finished !");
        if(fileListener!=null)
            if(downloadResult==null){
            downloadResult="success";
            }
            if(failThreadNum.get()>0){


            }
            fileListener.afterDownload(downloadResult);
    }

    private void startDownloadTask(int start, int end) {
        Thread thread = new Thread(() -> {


            logger.info("start to download file !");
            loadData(start, end);

        }

        );
        thread.start();
        threadList.add(thread);
    }

    private ThreadLocal<Integer> threadLocal=new ThreadLocal();

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int increaseVal = 0;//download timeout ,recevivedLength  resume to be initVal

        int n;
        byte[] buffer = new byte[4096];
        for(; -1 != (n = input.read(buffer)); ) {
            increaseVal+=n;
            threadLocal.set(increaseVal);
           this.recevivedLength.addAndGet(n);
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private void loadData(int start, int end) {
        InputStream inputStream = null;
        try{
            HttpURLConnection connection;
            if(proxy!=null){
                connection = (HttpURLConnection) new URL(url).openConnection(proxy);
            }else{
                connection=(HttpURLConnection) new URL(url).openConnection();
            }


//                connection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1; .NET CLR 2.0.50727");
            connection.addRequestProperty("Range", "bytes=" + start + "-" + end);
            connection.setReadTimeout(60*1000);
            connection.setConnectTimeout(60*1000);//todo   setReadTimeout setConnectTimeout  how to work
            inputStream = connection.getInputStream();
            byte[] bytes = toByteArray(inputStream);//todo principle of this method
            randomAccessFile.seek(start);
            randomAccessFile.write(bytes);
            logger.info(" finished task !");
        } catch (IOException e) {
            Integer increaseVal = threadLocal.get();
            recevivedLength.set(recevivedLength.get()-increaseVal);
            if(e instanceof SocketTimeoutException){
                logger.error(e.getMessage()+",retry again!");
                loadData(start,end);
            }else{
                downloadFail(e.getMessage());

            }

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }




    private  String convertFileSize(long size) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;

        if (size >= gb) {
            return String.format("%.1f GB", (float) size / gb);
        } else if (size >= mb) {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f MB" : "%.1f MB", f);
        } else if (size >= kb) {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f KB" : "%.1f KB", f);
        } else
            return String.format("%d B", size);
    }



    interface FileListener{
        /**
         * before download
         */
        void beforeDownload();

        /**
         * downloading
         */
        void downloading();

        /**
         * after download
         * @param downloadResult
         */
        void afterDownload(String downloadResult);
    }

}


//todo 1.break point retry(断点续传)   how to test this function point=>disconnect internet and re connect
//todo 2.accele download speed
// 3.illegal  fileName filter
//todo 4.progress bar  //there is a bug   then timeout
//todo 5.email tips
//todo 6.logger print log,remove System.out.println