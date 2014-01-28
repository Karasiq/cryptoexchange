package com.springapp.cryptoexchange;

import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@EnableWebMvcSecurity
@Controller
@RequestMapping("/")
public class MainController {
    @RequestMapping
    public String indexPage() {
        return "redirect:static/index.html";
    }
}
