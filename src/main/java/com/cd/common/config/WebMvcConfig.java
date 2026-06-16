package com.cd.common.config;

import com.cd.common.license.LicenseFeatureInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LicenseFeatureInterceptor licenseFeatureInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get("uploads").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(licenseFeatureInterceptor)
                .addPathPatterns("/api/**", "/vulnRule/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/current",
                        "/api/user/logout",
                        "/api/user/updateSelf",
                        "/api/user/avatar/upload",
                        "/api/user/changePassword",
                        "/api/host/**",
                        "/api/dashboard/**",
                        "/api/platform/**",
                        "/api/license/activate"
                );
    }
}
