package com.workbench.app.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 单域名部署时的前端路由回退。
 *
 * <p>静态文件（index.html、assets/*.js）由 Spring Boot 默认的静态资源处理器直接从 jar 里读，
 * 这里只负责 history 路由：文件不存在的深链接要回到对应端的 index.html，否则刷新 404。</p>
 *
 * <p>路径里的 {@code [^\.]*} 用来排除带扩展名的资源请求，避免把 /admin/assets/x.js 也转发掉。</p>
 */
@Controller
public class SpaForwardController {

    /** 员工端：/ 由 welcome page 处理，其余深链接走这里。 */
    @GetMapping({
            "/login",
            "/reports",
            "/starmap",
            "/settings",
            "/projects",
            "/projects/{a:[^\\.]*}",
            "/projects/{a:[^\\.]*}/{b:[^\\.]*}"
    })
    public String staff() {
        return "forward:/index.html";
    }

    /** 管理端。 */
    @GetMapping({
            "/admin",
            "/admin/",
            "/admin/{a:[^\\.]*}",
            "/admin/{a:[^\\.]*}/{b:[^\\.]*}"
    })
    public String admin() {
        return "forward:/admin/index.html";
    }

    /** 移动端。 */
    @GetMapping({
            "/m",
            "/m/",
            "/m/{a:[^\\.]*}",
            "/m/{a:[^\\.]*}/{b:[^\\.]*}"
    })
    public String mobile() {
        return "forward:/m/index.html";
    }
}
