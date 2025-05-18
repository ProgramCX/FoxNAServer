package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.security.JwtAuthenticationFilter;
import cn.programcx.foxnaserver.service.UserDetailService;
import cn.programcx.foxnaserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Autowired
    private UserDetailService userDetailsService;

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil ) {
        this.jwtUtil = jwtUtil;
    }

    // 配置密码加密器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    // 配置身份认证提供者
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService); // 用于从数据库加载用户信息
        provider.setPasswordEncoder(passwordEncoder());     // 密码加密器
        return provider;
    }

    // 配置 AuthenticationManager（用于登录接口）
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 注册的 JwtAuthenticationFilter
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    // 核心安全过滤链配置
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/status/**").permitAll()// 允许所有用户访问
                .antMatchers("/api/file/**").hasAuthority("FILE")
                .antMatchers("/api/stream/**").hasAuthority("STREAM")
                .antMatchers("/api/ddns/**").hasAuthority("DDNS")
                .antMatchers("/api/mail/**").hasAuthority("MAIL")
                .antMatchers("/api/user/**").hasAuthority("USER")
                .anyRequest().authenticated(); // 使用表单登录
        return http.build();
    }
}
