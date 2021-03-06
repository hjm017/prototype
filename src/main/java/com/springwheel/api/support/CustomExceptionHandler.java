package com.springwheel.api.support;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.springwheel.common.annotation.ApiController;
import com.springwheel.common.annotation.ParamCheck;
import com.springwheel.common.exception.ApiException;
import com.springwheel.common.exception.ParamCheckException;
import com.springwheel.common.mapper.JsonMapper;
import com.springwheel.common.util.constants.MediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.google.common.collect.Maps;
/**
 * @author hjm
 * @Time 2016/5/1 20:25.
 */
@ControllerAdvice(basePackages = {"com.springwheel.api"})
public class CustomExceptionHandler extends ResponseEntityExceptionHandler {

    private Logger logger = LoggerFactory.getLogger(CustomExceptionHandler.class);

    private JsonMapper jsonMapper = new JsonMapper();

    @ExceptionHandler(value = {ApiException.class})
    public final ResponseEntity<ErrorResult> handleApiException(ApiException ex, HttpServletRequest request) {
        // 注入servletRequest，用于出错时打印请求URL与来源地址
        logError(ex, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.JSON_UTF_8));
        ErrorResult result = new ErrorResult(ex.getErrorCode().code, ex.getMessage());
        return new ResponseEntity<ErrorResult>(result, headers, HttpStatus.valueOf(ex.getErrorCode().httpStatus));
    }

    @ExceptionHandler(value = {ParamCheckException.class})
    public final ResponseEntity<ErrorResult> handleParamCheckException(ApiException ex, HttpServletRequest request) {
        // 注入servletRequest，用于出错时打印请求URL与来源地址
        logError(ex, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.JSON_UTF_8));
        ErrorResult result = new ErrorResult(ex.getErrorCode().code, ex.getMessage());
        return new ResponseEntity<ErrorResult>(result, headers, HttpStatus.valueOf(ex.getErrorCode().httpStatus));
    }


    @ExceptionHandler(value = { Exception.class })
    public final ResponseEntity<ErrorResult> handleGeneralException(Exception ex, HttpServletRequest request) {
        logError(ex, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.JSON_UTF_8));
        ErrorResult result = new ErrorResult(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        return new ResponseEntity<ErrorResult>(result, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 重载ResponseEntityExceptionHandler的方法，加入日志
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {

        logError(ex);

        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            request.setAttribute("javax.servlet.error.exception", ex, WebRequest.SCOPE_REQUEST);
        }

        return new ResponseEntity<Object>(body, headers, status);
    }

    public void logError(Exception ex) {
        Map<String, String> map = Maps.newHashMap();
        map.put("message", ex.getMessage());
        logger.error(jsonMapper.toJson(map), ex);
    }

    public void logError(Exception ex, HttpServletRequest request) {
        Map<String, String> map = Maps.newHashMap();
        map.put("message", ex.getMessage());
        map.put("from", request.getRemoteAddr());
        String queryString = request.getQueryString();
        map.put("path", queryString != null ? (request.getRequestURI() + "?" + queryString) : request.getRequestURI());

        logger.error(jsonMapper.toJson(map), ex);
    }
}