package com.yang;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.*;
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
    private String url;
    private String destinationPath;

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


    public void setListener(FileListener fileListener) {
        this.fileListener = fileListener;
    }


    public void downloadFail(String message) {
        failThreadNum.incrementAndGet();
        System.out.println(Thread.currentThread().getName()+" download fail ! error info=> "+message);
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
        System.out.println("file size is "+FileDownloader.convertFileSize(fileLength));
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
        System.out.println("fileFullPath："+fileFullPath);
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
        System.out.println("file downloading .....");
        while (finishedThread.size()<threadNum&&failThreadNum.get()<1){

            synchronized (threadList){
                threadList.forEach(t->{
                    if(!t.isAlive()){
                        finishedThread.add(t);
                    }
                });
            }


        }



        if(failThreadNum.get()>0){
            try {
             file=   new File(fileFullPath);
                if(file.exists()){
                    randomAccessFile.close();//todo if no this step ,file is opening then next step will fail
                    FileUtils.forceDelete(file);
                }

            } catch (IOException e) {
                System.out.println(" delete file fail ! error info :"+e.getMessage());
            }
        }


        System.out.println("file download finished !");
        if(fileListener!=null)
            fileListener.afterDownload();
    }

    private void startDownloadTask(int start, int end) {
        Thread thread = new Thread(() -> {


            System.out.println(Thread.currentThread().getName() + " start to download file !");
            loadData(start, end);

        }

        );
        thread.start();
        threadList.add(thread);
    }

    private void loadData(int start, int end) {
        InputStream inputStream = null;
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1", 8888));
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);

//                connection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1; .NET CLR 2.0.50727");
            connection.addRequestProperty("Range", "bytes=" + start + "-" + end);
            connection.setReadTimeout(60*1000);
            connection.setConnectTimeout(60*1000);//todo   setReadTimeout setConnectTimeout  how to work
            inputStream = connection.getInputStream();
            byte[] bytes = IOUtils.toByteArray(inputStream);//todo principle of this method
            randomAccessFile.seek(start);
            randomAccessFile.write(bytes);
            System.out.println(Thread.currentThread().getName() + " finished task !");
        } catch (IOException e) {
            if(e instanceof SocketTimeoutException){
                System.out.println(Thread.currentThread().getName()+"=="+e.getMessage()+",retry again!");
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
         */
         void afterDownload();
    }



    public static String convertFileSize(long size) {
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
}


//todo 1.break point retry(断点续传)   how to test this function point=>disconnect internet and re connect
//todo 2.accele download speed
//todo 3.illegal  fileName filter
//todo 4.progress bar
//todo 5.email tips