package cn.net.pap.example.wechat.service;

import cn.net.pap.example.wechat.vo.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service("wechat")
public class WeChatService {

    private static final Logger log = LoggerFactory.getLogger(WeChatService.class);

    @Value("${wechat.appID:wx8cfed509a7a7b591}")
    private String wechatAppID;

    @Value("${wechat.appsecret:3433440ccebe845a1237fc6d046655ce}")
    private String wechatAppsecret;

    private final ConcurrentHashMap<String, String> wechatMap = new ConcurrentHashMap<>();

    /**
     * 获取稳定版接口调用凭据
     * https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/getStableAccessToken.html
     * @return
     */
    public Result<String> getStableAccessToken() {
        try {
            boolean isCall = false;
            if(!wechatMap.isEmpty() && wechatMap.containsKey("access_token")
                    && wechatMap.containsKey("expires_in") && wechatMap.containsKey("expires_in_timeswap")) {
                String expires_in_timeswap = wechatMap.get("expires_in_timeswap");
                String currTimeswap = String.valueOf(System.currentTimeMillis() / 1000);
                if(currTimeswap.compareTo(expires_in_timeswap) >= 0) {
                    isCall = true;
                }
            } else {
                isCall = true;
            }
            if(isCall) {
                Map<String, String> paramMap = new HashMap<String, String>();
                paramMap.put("grant_type", "client_credential");
                paramMap.put("appid", wechatAppID);
                paramMap.put("secret", wechatAppsecret);
                ObjectMapper objectMapper = new ObjectMapper();
                String resultStr = sendPostByHttpClient("https://api.weixin.qq.com/cgi-bin/stable_token", objectMapper.writeValueAsString(paramMap));
                JsonNode jsonNode = objectMapper.readTree(resultStr);
                if(jsonNode.get("errcode") != null){
                    return Result.error(jsonNode.get("errcode").asText());
                } else {
                    String accessToken = jsonNode.get("access_token").asText();
                    String expiresIn = jsonNode.get("expires_in").asText();
                    wechatMap.put("access_token", accessToken);
                    wechatMap.put("expires_in", (Integer.parseInt(expiresIn) / 2) + "");
                    wechatMap.put("expires_in_timeswap", (System.currentTimeMillis() / 1000 + ((Integer.parseInt(expiresIn) / 2) * 60)) + "");
                    return Result.success(wechatMap.get("access_token").toString());
                }
            } else {
                return Result.success(wechatMap.get("access_token").toString());
            }
        } catch (JsonProcessingException e) {
            log.error("getStableAccessToken failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户基本信息
     * https://developers.weixin.qq.com/doc/offiaccount/User_Management/Get_users_basic_information_UnionID.html#UinonId
     * @return
     */
    public Result<Object> cgibin_user_info(String code) {
        try {
            Result<String> accessTokenResult = getStableAccessToken();
            String param = "access_token=" + accessTokenResult.getMessage() + "&code=" + code;
            String resultStr = sendGet("https://api.weixin.qq.com/cgi-bin/user/info", param,"UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(resultStr);
            if(jsonNode.get("errcode") != null){
                return Result.error(jsonNode.get("errcode").asText());
            } else {
                return Result.success(jsonNode.get("openid").asText());
            }
        } catch (JsonProcessingException e) {
            log.error("cgibin_user_info failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * https://developers.weixin.qq.com/doc/offiaccount/User_Management/Get_users_basic_information_UnionID.html#UinonId
     * @param openid
     * @return
     */
    public Result<Object> cgibin_user_info_UnionID(String openid) {
        try {
            Result<String> accessTokenResult = getStableAccessToken();
            String param = "access_token=" + accessTokenResult.getMessage() + "&openid=" + openid;
            String resultStr = sendGet("https://api.weixin.qq.com/cgi-bin/user/info", param,"UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(resultStr);
            if(jsonNode.get("errcode") != null){
                return Result.error(jsonNode.get("errcode").asText());
            } else {
                return Result.successObj(jsonNode);
            }
        } catch (JsonProcessingException e) {
            log.error("cgibin_user_info_UnionID failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html#3
     * @param code
     * @return
     */
    public Result<String> sns_oauth2_access_token(String code) {
        try {
            String param = "appid=" + wechatAppID + "&secret=" + wechatAppsecret + "&code=" + code + "&grant_type=authorization_code";
            String resultStr = sendGet("https://api.weixin.qq.com/sns/oauth2/access_token", param,"UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(resultStr);
            if(jsonNode.get("errcode") != null){
                return Result.error(jsonNode.get("errcode").asText());
            } else {
                return Result.success(jsonNode.get("openid").asText());
            }
        } catch (JsonProcessingException e) {
            log.error("sns_oauth2_access_token failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * https://developers.weixin.qq.com/doc/offiaccount/User_Management/Configuring_user_notes.html
     * @param openid
     * @param remark
     * @return
     */
    public Result<String> cgibin_user_info_updateremark(String openid, String remark) {
        try {
            Result<String> accessTokenResult = getStableAccessToken();
            String urlParam = "access_token=" + accessTokenResult.getMessage();

            Map<String, String> paramMap = new HashMap<String, String>();
            paramMap.put("openid", openid);
            paramMap.put("remark", remark);
            ObjectMapper objectMapper = new ObjectMapper();
            String resultStr = sendPostByHttpClient("https://api.weixin.qq.com/cgi-bin/user/info/updateremark?" + urlParam, objectMapper.writeValueAsString(paramMap));
            log.info("{}", resultStr);
            JsonNode jsonNode = objectMapper.readTree(resultStr);
            if(jsonNode.get("errcode") != null && jsonNode.get("errcode").asText().equals("0")){
                return Result.success("设置成功!");
            } else {
                return Result.error(jsonNode.get("errmsg").asText());
            }
        } catch (JsonProcessingException e) {
            log.error("cgibin_user_info_updateremark failed", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Thread sleep
     * @return
     */
    public Result<String> sleep() {
        try {
            log.info("enter {}", System.currentTimeMillis());

            Thread.sleep(10000);

            return Result.success("finish");
        } catch (InterruptedException e) {
            log.error("wechat sleep interrupted", e);
            Thread.currentThread().interrupt();
            return Result.error(e.getMessage());
        }
    }

    /**
     * 向指定 URL 发送GET方法的请求
     *
     * @param url 发送请求的 URL
     * @param param 请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
     * @param contentType 编码类型
     * @return 所代表远程资源的响应结果
     */
    public static String sendGet(String url, String param, String contentType)
    {
        StringBuilder result = new StringBuilder();
        try
        {
            String urlNameString = !StringUtils.isEmpty(param) ? url + "?" + param : url;
            log.info("sendGet - {}", urlNameString);
            URL realUrl = new URL(urlNameString);
            URLConnection connection = realUrl.openConnection();
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.connect();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), contentType))) {
                String line;
                while ((line = in.readLine()) != null)
                {
                    result.append(line);
                }
            }
            log.info("recv - {}", result);
        }
        catch (ConnectException e)
        {
            log.error("调用HttpUtils.sendGet ConnectException, url={}, param={}", url, param, e);
        }
        catch (SocketTimeoutException e)
        {
            log.error("调用HttpUtils.sendGet SocketTimeoutException, url={}, param={}", url, param, e);
        }
        catch (IOException e)
        {
            log.error("调用HttpUtils.sendGet IOException, url={}, param={}", url, param, e);
        }
        catch (Exception e)
        {
            log.error("调用HttpsUtil.sendGet Exception, url={}, param={}", url, param, e);
        }
        return result.toString();
    }


    public static String sendPostByHttpClient(String url, String param) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(param)) {
            return null;
        }

        String result = null;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);

            httpPost.setEntity(EntityBuilder.create().setText(param).setContentType(ContentType.APPLICATION_JSON).build());

            httpPost.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    HttpEntity entity = response.getEntity();
                    result = EntityUtils.toString(entity);
                }
            }

        } catch (IOException e) {
            log.error("调用HttpUtils.sendPostByHttpClient IOException, url={}", url, e);
        } catch (Exception e) {
            log.error("调用HttpUtils.sendPostByHttpClient Exception, url={}", url, e);
        }

        return result;
    }

    private static RequestConfig builderRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.of(5000, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(5000, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(120000, TimeUnit.MILLISECONDS)).build();
    }


}
