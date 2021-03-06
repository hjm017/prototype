package com.springwheel.interceptor;

import com.springwheel.api.support.ErrorCode;
import com.springwheel.common.annotation.ParamCheck;
import com.springwheel.common.exception.ParamCheckException;
import com.springwheel.common.validation.ValidationResult;
import com.springwheel.common.validation.ValidationUtils;
import org.hibernate.validator.internal.engine.ValidatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.HandlerMethodSelector;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.groups.Default;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参数校验拦截器
 *
 * @author hjm
 * @Time 2016/5/2 10:56.
 */
@Component
public class ValidationInterceptor implements HandlerInterceptor {

    private Logger logger = LoggerFactory.getLogger(getClass());


    private List<HandlerMethodArgumentResolver> argumentResolvers;

    @Autowired
    private Validator validator;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache =
            new ConcurrentHashMap<MethodParameter, HandlerMethodArgumentResolver>(256);
    private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<Class<?>, Set<Method>>(64);

    @Autowired
    public ValidationInterceptor(RequestMappingHandlerAdapter requestMappingHandlerAdapter){
        argumentResolvers = requestMappingHandlerAdapter.getArgumentResolvers();
    }

    //****************************************//
    //                                        //
    //            Override Method             //
    //                                        //
    //****************************************//

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {

        LocalValidatorFactoryBean validatorFactoryBean = (LocalValidatorFactoryBean)validator;
        ValidatorImpl validatorImpl = (ValidatorImpl) validatorFactoryBean.getValidator();
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        HandlerMethod method = (HandlerMethod)handler;
        ParamCheck valid = method.getMethodAnnotation(ParamCheck.class);
        if(valid!=null){
            Class<?>[] groups = new Class<?>[0];
            MethodParameter[] parameters = method.getMethodParameters();
            Object[] parameterValues = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                MethodParameter parameter = parameters[i];
                HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);
                Assert.notNull(resolver, "Unknown parameter type [" + parameter.getParameterType().getName() + "]");
                ModelAndViewContainer mavContainer = new ModelAndViewContainer();
                mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
                WebDataBinderFactory webDataBinderFactory = getDataBinderFactory(method);
                Object value = resolver.resolveArgument(parameter, mavContainer, webRequest, webDataBinderFactory);
                parameterValues[i] = value;
            }

            ValidationResult validationResult = checkParam(validatorImpl,parameterValues,method,groups);

            if (validationResult.isHasErrors()) {
                throw new ParamCheckException(validationResult.getErrorMsg().toString(),
                        ErrorCode.BAD_REQUEST);
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response, Object handler, Exception ex)
            throws Exception {

    }


    //****************************************//
    //                                        //
    //            define Method               //
    //                                        //
    //****************************************//

    private  ValidationResult  checkParam(ValidatorImpl validatorImpl, Object[] parameterValues,HandlerMethod method,Class<?>[] groups){

        Set<ConstraintViolation<Object>> violations = null;
        Map<String, String> errorMsg = new HashMap<String, String>();
        //检验对象
        for (Object obj : parameterValues) {
            if (obj != null) {
                violations = validatorImpl.validate(obj, Default.class);
                ValidationResult result = ValidationUtils.getValidationResult(violations);
                if (result.isHasErrors()) {
                    errorMsg.putAll(result.getErrorMsg());
                }
            }
        }

        //检验方法参数
        violations = validatorImpl.validateParameters(method.getBean(), method.getMethod(),
                parameterValues, groups);
        ValidationResult result = ValidationUtils.getValidationResult(violations);
        if (result.isHasErrors()) {
            errorMsg.putAll(result.getErrorMsg());
        }

        if (!errorMsg.isEmpty()) {
            ValidationResult validationResult = new ValidationResult();
            validationResult.setHasErrors(true);
            validationResult.setErrorMsg(errorMsg);
            return validationResult;
        }

        //如果都没有异常，返回默认值
        return new ValidationResult(false);

    }


    private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
        Class<?> handlerType = handlerMethod.getBeanType();
        Set<Method> methods = this.initBinderCache.get(handlerType);
        if (methods == null) {
            methods = HandlerMethodSelector.selectMethods(handlerType, RequestMappingHandlerAdapter.INIT_BINDER_METHODS);
            this.initBinderCache.put(handlerType, methods);
        }
        List<InvocableHandlerMethod> initBinderMethods = new ArrayList<InvocableHandlerMethod>();
        for (Method method : methods) {
            Object bean = handlerMethod.getBean();
            initBinderMethods.add(new InvocableHandlerMethod(bean, method));
        }
        return new ServletRequestDataBinderFactory(initBinderMethods, adapter.getWebBindingInitializer());
    }

    private HandlerMethodArgumentResolver getArgumentResolver(
            MethodParameter parameter) {
        HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
        if (result == null) {
            for (HandlerMethodArgumentResolver methodArgumentResolver : this.argumentResolvers) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Testing if argument resolver [" + methodArgumentResolver + "] supports [" +
                            parameter.getGenericParameterType() + "]");
                }
                if (methodArgumentResolver.supportsParameter(parameter)) {
                    result = methodArgumentResolver;
                    this.argumentResolverCache.put(parameter, result);
                    break;
                }
            }
        }
        return result;
    }



}
