//package com.docauth.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.web.SecurityFilterChain;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//            // 禁用 CSRF（因为使用 Token 认证）
//            .csrf(csrf -> csrf.disable())
//            // 配置授权规则
//            .authorizeHttpRequests(auth -> auth
//                // 允许登录接口匿名访问
//                .requestMatchers("/account/login").permitAll()
//                // 其他请求需要认证
//                .anyRequest().authenticated()
//            )
//            // 禁用 HTTP Basic 认证（使用自定义 Token 认证）
//            .httpBasic(basic -> basic.disable())
//            // 禁用表单登录
//            .formLogin(form -> form.disable())
//            // 禁用注销（使用 Token 机制）
//            .logout(logout -> logout.disable())
//            // 配置会话管理为无状态
//            .sessionManagement(session -> session
//                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//            );
//
//        return http.build();
//    }
//}
