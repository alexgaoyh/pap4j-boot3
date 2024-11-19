package cn.net.pap.example.wechat.controller;

import cn.net.pap.example.wechat.service.WeChatService;
import cn.net.pap.example.wechat.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wechat/api")
public class WeChatController {

    @Autowired
    private WeChatService weChatService;


    @GetMapping(value = "/cgi-bin/user/info")
    public Result<Object> userInfo(String openId) {
        Result<Object> userInfoResult = weChatService.cgibin_user_info(openId);
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

}
