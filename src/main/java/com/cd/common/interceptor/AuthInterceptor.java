package com.cd.common.interceptor;

import com.cd.common.Result;
import com.cd.common.auth.LoginSessionManager;
import com.cd.common.constant.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final LoginSessionManager loginSessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorizationHeader = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);
        String token = extractToken(authorizationHeader);
        Long userId = StringUtils.hasText(token) ? loginSessionManager.getUserId(token) : null;

        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, "未登录或登录状态已失效")));
            return false;
        }

        request.setAttribute(AuthConstants.CURRENT_USER_ID, userId);
        request.setAttribute(AuthConstants.CURRENT_TOKEN, token);
        return true;
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(AuthConstants.BEARER_PREFIX.length());
    }
}
