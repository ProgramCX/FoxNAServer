package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.security.JwtAuthenticationFilter;
import cn.programcx.foxnaserver.service.user.UserDetailService;
import cn.programcx.foxnaserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
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

    // CORS 配置
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // 核心安全过滤链配置
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws/overview/**").permitAll()
                        .requestMatchers("/api/status/**").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/api/file/media/validate").hasAuthority("FILE")
                        .requestMatchers("/api/file/media/metadata").hasAuthority("FILE")
                        .requestMatchers("/api/file/media/**").permitAll()
                        .requestMatchers("/api/file/**").hasAuthority("FILE")
                        .requestMatchers("/api/stream/**").hasAuthority("STREAM")
                        .requestMatchers("/api/ddns/**").hasAuthority("DDNS")
                        .requestMatchers("/api/mail/**").hasAuthority("MAIL")
                        .requestMatchers("/api/user/**").hasAuthority("USER")
                        .requestMatchers("/api/ssh/**").hasAuthority("SSH")
                        .requestMatchers("/api/state/**").authenticated()
                        .requestMatchers("/api/hardware/**").authenticated()
                        .requestMatchers("/api/user-self/**").authenticated()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

}
