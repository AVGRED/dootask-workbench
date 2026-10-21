package com.workbench.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 单域名部署时的前端托管：
 *
 * <ul>
 *   <li>{@code /}        → 员工端（dist 根目录）</li>
 *   <li>{@code /admin/**} → 管理端（dist/admin）</li>
 *   <li>{@code /m/**}     → 移动端（dist/m）</li>
 * </ul>
 *
 * <p>三个前端都是 history 路由，找不到静态文件时要回退到各自的 index.html，
 * 否则刷新 /admin/team 这类深链接会 404。</p>
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver("classpath:/static/admin/index.html"));
        registry.addResourceHandler("/m/**")
                .addResourceLocations("classpath:/static/m/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver("classpath:/static/m/index.html"));
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver("classpath:/static/index.html"));
    }

    /** 找不到文件（或请求的是目录）时返回 index.html，交给前端路由处理。 */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        private final Resource index;

        private SpaFallbackResolver(String indexPath) {
            this.index = new ClassPathResource(indexPath);
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws java.io.IOException {
            if (resourcePath.isEmpty() || resourcePath.endsWith("/")) {
                return index;
            }
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            return index.exists() ? index : null;
        }
    }
}
