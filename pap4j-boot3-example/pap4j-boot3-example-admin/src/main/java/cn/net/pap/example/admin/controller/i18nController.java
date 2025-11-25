package cn.net.pap.example.admin.controller;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@RestController
public class i18nController {

    @Autowired
    private MessageSource messageSource;

    @GetMapping("/greeting")
    public String greeting(@RequestHeader(value = "Accept-Language", required = false) Locale locale) {
        return messageSource.getMessage("greeting.message", null, locale);
    }

}
