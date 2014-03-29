package com.springapp.cryptoexchange;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@CommonsLog
@ControllerAdvice
public class GlobalHandler {
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @ExceptionHandler(Exception.class)
    void onException(Exception exception, HttpServletResponse httpServletResponse) throws IOException {
        exception.printStackTrace();
        if (exception instanceof AccessDeniedException) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        httpServletResponse.getWriter().write(String.format("%s: %s", exception.getClass().getSimpleName(), exception.getLocalizedMessage()));
    }
}
