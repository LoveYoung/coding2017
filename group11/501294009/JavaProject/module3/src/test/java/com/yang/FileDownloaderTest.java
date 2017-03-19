package com.yang;

import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Properties;

/**
 * Created by Dev_yang on 2017/3/18.
 */
public class FileDownloaderTest {

    private Logger logger= LoggerFactory.getLogger(FileDownloaderTest.class);


    @Before
    public void setUp() throws Exception {

        BasicConfigurator.configure();
    }

    @Test
    public void startDownload() throws Exception {

        String url = "http://down.360safe.com/setup.exe";
      url = "http://upload.69xiu.com/upload/roomimg/2016/01/05/20965598568b4f55cfb7a_370x280.jpg";
//        url = "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1489851600015&di=a45a03f4ac7e6156647f81b889d89cda&imgtype=0&src=http%3A%2F%2Fattach.bbs.miui.com%2Fforum%2F201702%2F19%2F110217jqq4hmqs9taqy6sy.jpg";
//       url = "http://dl90.80s.im:920/1701/[斗破苍穹]第04集/[斗破苍穹]第04集_sd.mp4";//this url only xunlei can download   chrome also fail
//       url = "http://mp4.28mtv.com:9090/mp42/51544-%E6%9D%9C%E5%BE%B7%E4%BC%9F_%E6%9E%97%E5%87%A1-%E5%9B%A0%E4%B8%BA%E4%BD%A0[68mtv.com].mp4";//this internet file self has little trouble
//       url = "http://avi.68mtv.com:9090/avi1/22072-%E9%82%93%E7%B4%AB%E6%A3%8B-%E5%96%9C%E6%AC%A2%E4%BD%A0[68mtv.com].avi";
//       url = "http://sw.bos.baidu.com/sw-search-sp/software/3545f5720dafd/IQIYIsetup_bdtw_5.5.33.3550.exe";
        String destinationPath = "F:\\";
        FileDownloader fileDownloader = new FileDownloader(url, destinationPath, null, 9);
        fileDownloader.setListener(new FileDownloader.FileListener() {
            @Override
            public void beforeDownload() {
                System.out.println("beforeDownload");
            }

            @Override
            public void downloading() {

            }

            @Override
            public void afterDownload(String downloadResult) {
                try {
                    Properties props = new Properties();

                    // 开启debug调试
                    props.setProperty("mail.debug", "true");
                    // 发送服务器需要身份验证
                    props.setProperty("mail.smtp.auth", "true");
                    // 设置邮件服务器主机名
                    props.setProperty("mail.host", "smtp.qq.com");
                    // 发送邮件协议名称
                    props.setProperty("mail.transport.protocol", "smtp");

                    MailSSLSocketFactory sf = new MailSSLSocketFactory();
                    sf.setTrustAllHosts(true);
                    props.put("mail.smtp.ssl.enable", "true");
                    props.put("mail.smtp.ssl.socketFactory", sf);

                    Session session = Session.getInstance(props);

                    Message msg = new MimeMessage(session);
                    msg.setSubject("file download result");
                    msg.setText(downloadResult);
                    msg.setFrom(new InternetAddress("501294009@qq.com"));

                    Transport transport = session.getTransport();
                    transport.connect("smtp.qq.com", "501294009@qq.com", "keiicohbpriebjdi");

                    transport.sendMessage(msg, new Address[] { new InternetAddress("zhaomeinan0527@qq.com") });
                    transport.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        fileDownloader.setProxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1", 8888)));
        fileDownloader.startDownload();
    }


    @Test
    public void name() throws Exception {
        logger.info("test");

    }
}