package com.springapp.cryptoexchange;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class GlobalHandler {
    @ExceptionHandler(Exception.class)
    void onException(Exception exception, HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        httpServletResponse.getWriter().write(String.format("%s: %s", exception.getClass().getSimpleName(), exception.getLocalizedMessage()));
    }
}
