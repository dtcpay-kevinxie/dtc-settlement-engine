//package top.dtc.settlement.module.wechat.service;
//
//import lombok.extern.log4j.Log4j2;
//import top.dtc.settlement.constant.Constant;
//import top.dtc.settlement.module.wechat.core.properties.WechatProperties;
//import top.dtc.settlement.util.PayoutUtils;
//import org.apache.http.HttpEntity;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.config.RequestConfig;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.conn.ConnectTimeoutException;
//import org.apache.http.conn.ConnectionPoolTimeoutException;
//import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//import org.apache.http.conn.ssl.SSLContexts;
//import org.apache.http.entity.StringEntity;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.util.EntityUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//
//import javax.net.ssl.SSLContext;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.SocketTimeoutException;
//import java.security.*;
//import java.security.cert.CertificateException;
//
//@Log4j2
//@Service
//public class WechatHttpsService {
//
//    @Autowired
//    private WechatProperties wechatProperties;
//
//    @Autowired
//    private WechatAccountInfoService wechatAccountInfoService;
//
//    //Indicates whether the requester has already done initialization
//    private boolean hasInit = false;
//
//    //Connection timeout time, default 10 seconds
//    private int socketTimeout = 10000;
//
//    //Transfer timeout time, default 30 seconds
//    private int connectTimeout = 30000;
//
//    //Configuration of requester
//    private RequestConfig requestConfig;
//
//    //HTTP requester
//    private CloseableHttpClient httpClient;
//
//    private void init(String currency) throws IOException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException,
//            KeyManagementException {
//        String certLocalPath = wechatAccountInfoService.getWechatAccountInfo(wechatProperties.certLocalPath, currency);
//        String certPassword = wechatAccountInfoService.getWechatAccountInfo(wechatProperties.certPassword, currency);
//
//        KeyStore keyStore = KeyStore.getInstance("PKCS12");
//        //Load the local certificate for HTTPS encrypted transmission
//        try (InputStream instream = certLocalPath.startsWith("classpath:") ?
//                this.getClass().getResourceAsStream(certLocalPath.substring(10)) :
//                new FileInputStream(new File(certLocalPath))) {
//            keyStore.load(instream, certPassword.toCharArray());//Set certificate password
//        } catch (CertificateException | NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        }
//
//        // Trust own CA and all self-signed certs
//        SSLContext sslcontext = SSLContexts.custom()
//                .loadKeyMaterial(keyStore, certPassword.toCharArray()).build();
//        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[]{wechatProperties.wechatTLS},
//                null, SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
//
//        httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
//
//        //Initializes requestConfig according to the default timeout limit
//        requestConfig = RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout).build();
//
//        hasInit = true;
//    }
//
//    /**
//     * Data to API, post, XML via Https
//     *
//     * @param url        https url
//     * @param xmlMessage request xml object
//     * @return
//     * @throws IOException
//     * @throws KeyStoreException
//     * @throws UnrecoverableKeyException
//     * @throws NoSuchAlgorithmException
//     * @throws KeyManagementException
//     */
//    public String sendPost(String url, WalletMessage xmlMessage, String currency) throws UnrecoverableKeyException,
//            KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
//
//        if (!hasInit) {
//            init(currency);
//        }
//        String result = null;
//
//        HttpPost httpPost = new HttpPost(url);
//
//        // The data object to be submitted to API is converted into XML format, and data Post is given to API
//        String postDataXML = PayoutUtils.messageToXml(xmlMessage);
//        logger.info("API, POST, the data in the past is: " + postDataXML);
//
//        StringEntity postEntity = new StringEntity(postDataXML, Constant.CHARSET);
//        httpPost.addHeader("Content-Type", MediaType.TEXT_XML_VALUE);
//        httpPost.setEntity(postEntity);
//
//        // Set the configuration of the requester
//        httpPost.setConfig(requestConfig);
//        logger.info("executing request" + httpPost.getRequestLine());
//
//        try {
//            HttpResponse response = httpClient.execute(httpPost);
//
//            HttpEntity entity = response.getEntity();
//
//            result = EntityUtils.toString(entity, Constant.CHARSET);
//
//        } catch (ConnectionPoolTimeoutException e) {
//            logger.error("http get throw ConnectionPoolTimeoutException(wait time out)", e);
//
//        } catch (ConnectTimeoutException e) {
//            logger.error("http get throw ConnectTimeoutException", e);
//
//        } catch (SocketTimeoutException e) {
//            logger.error("http get throw SocketTimeoutException", e);
//
//        } catch (Exception e) {
//            logger.error("http get throw Exception", e);
//
//        } finally {
//            httpPost.abort();
//        }
//        logger.info("API, the returned data is: " + result);
//
//        return result;
//    }
//
//}
