package cn.net.pap.example.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@Tag(name = "国际化接口", description = "提供不同语言环境下的问候语")
public class i18nController {

    private final MessageSource messageSource;

    public i18nController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Operation(summary = "默认问候语", description = "根据 Accept-Language 请求头返回对应区域语言的问候语。")
    @GetMapping("/greeting")
    public String greeting(@Parameter(description = "语言环境标识") @RequestHeader(value = "Accept-Language", required = false) Locale locale) {
        return messageSource.getMessage("greeting.message", null, locale);
    }

    @Operation(summary = "带参数的问候语", description = "根据 Accept-Language 请求头返回对应区域语言且携带了默认参数的问候语。")
    @GetMapping("/greeting2")
    public String greeting2(@Parameter(description = "语言环境标识") @RequestHeader(value = "Accept-Language", required = false) Locale locale) {
        Object[] args = {"alexgaoyh"};
        String message = messageSource.getMessage("greeting.message2", args, locale);
        return message;
    }

}
