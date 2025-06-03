package cn.net.pap.example.wechat.controller;

import cn.net.pap.example.wechat.util.SpringUtils;
import cn.net.pap.example.wechat.service.WeChatService;
import cn.net.pap.example.wechat.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wechat/api")
public class WeChatController {

    private static final Logger logger = LoggerFactory.getLogger(WeChatController.class);

    @Autowired
    private WeChatService weChatService;


    @GetMapping(value = "/cgi-bin/user/info")
    public Result<Object> userInfo(String openId) {
        Result<Object> userInfoResult = weChatService.cgibin_user_info(openId);
        return userInfoResult;
    }

    @GetMapping(value = "/cgi-bin/user/infoUnionID")
    public Result<Object> userInfoUnionID(String openid) {
        Result<Object> userInfoResult = weChatService.cgibin_user_info_UnionID(openid);
        return userInfoResult;
    }

    @GetMapping(value = "/sns/oauth2/access_token")
    public Result<String> snsOauth2AccessToken(String code) {
        Result<String> userInfoResult = weChatService.sns_oauth2_access_token(code);
        return userInfoResult;
    }

    @GetMapping(value = "/cgi-bin/user/info/updateremark")
    public Result<String> updateremark(String openid, String remark) {
        Result<String> updateremark = weChatService.cgibin_user_info_updateremark(openid, remark);
        return updateremark;
    }

    @GetMapping(value = "/sleep")
    public Result<String> sleep() {
        try {
            Object wechat = SpringUtils.getBean("wechat");
            SpringUtils.invokeMethod(wechat, "sleep");
            System.out.println(wechat);
            return Result.success("finish");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping(value = "/logback")
    public Result<String> logback() {
        try {
            String resultStr = weChatService.sendPostByHttpClient("https://api.weixin.qq.com/cgi-bin/stable_token", "{}");
            logger.info("logback : {}", resultStr);
            return Result.success("finish");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

}
