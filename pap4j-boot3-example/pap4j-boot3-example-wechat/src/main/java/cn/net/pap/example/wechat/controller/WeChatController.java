package cn.net.pap.example.wechat.controller;

import cn.net.pap.example.wechat.util.SpringUtils;
import cn.net.pap.example.wechat.service.WeChatService;
import cn.net.pap.example.wechat.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wechat/api")
@Tag(name = "微信测试接口", description = "模拟微信公众号/小程序服务端相关 API 交互测试接口")
public class WeChatController {

    private static final Logger logger = LoggerFactory.getLogger(WeChatController.class);

    private final WeChatService weChatService;

    public WeChatController(WeChatService weChatService) {
        this.weChatService = weChatService;
    }

    @Operation(summary = "获取微信用户信息")
    @GetMapping(value = "/cgi-bin/user/info")
    public Result<Object> userInfo(@Parameter(description = "微信用户 OpenId") String openId) {
        Result<Object> userInfoResult = weChatService.cgibin_user_info(openId);
        return userInfoResult;
    }

    @Operation(summary = "获取带 UnionID 的用户信息")
    @GetMapping(value = "/cgi-bin/user/infoUnionID")
    public Result<Object> userInfoUnionID(@Parameter(description = "微信用户 OpenId") String openid) {
        Result<Object> userInfoResult = weChatService.cgibin_user_info_UnionID(openid);
        return userInfoResult;
    }

    @Operation(summary = "获取网页授权 AccessToken")
    @GetMapping(value = "/sns/oauth2/access_token")
    public Result<String> snsOauth2AccessToken(@Parameter(description = "授权 Code") String code) {
        Result<String> userInfoResult = weChatService.sns_oauth2_access_token(code);
        return userInfoResult;
    }

    @Operation(summary = "修改用户备注名")
    @GetMapping(value = "/cgi-bin/user/info/updateremark")
    public Result<String> updateremark(@Parameter(description = "微信用户 OpenId") String openid, @Parameter(description = "新备注名") String remark) {
        Result<String> updateremark = weChatService.cgibin_user_info_updateremark(openid, remark);
        return updateremark;
    }

    @Operation(summary = "方法反射休眠测试")
    @GetMapping(value = "/sleep")
    public Result<String> sleep() {
        try {
            Object wechat = SpringUtils.getBean("wechat");
            SpringUtils.invokeMethod(wechat, "sleep");
            logger.info("{}", wechat);
            return Result.success("finish");
        } catch (Exception e) {
            logger.error("wechat sleep invoke failed", e);
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "测试微信 stable_token 接口调用")
    @GetMapping(value = "/logback")
    public Result<String> logback() {
        try {
            String resultStr = weChatService.sendPostByHttpClient("https://api.weixin.qq.com/cgi-bin/stable_token", "{}");
            logger.info("logback : {}", resultStr);
            return Result.success("finish");
        } catch (Exception e) {
            logger.error("wechat stable_token request failed", e);
            return Result.error(e.getMessage());
        }
    }

}
