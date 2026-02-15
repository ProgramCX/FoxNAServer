package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.security.HttpCookieOAuth2AuthorizationRequestRepository;
import cn.programcx.foxnaserver.security.JwtAuthenticationFilter;
import cn.programcx.foxnaserver.security.OAuth2LoginFailureHandler;
import cn.programcx.foxnaserver.security.OAuth2LoginSuccessHandler;
import cn.programcx.foxnaserver.security.qq.QQOAuth2AccessTokenResponseClient;
import cn.programcx.foxnaserver.security.qq.QQOAuth2UserService;
import cn.programcx.foxnaserver.service.user.UserDetailService;
import cn.programcx.foxnaserver.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailService userDetailsService;

    @Autowired
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Autowired
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Autowired
    private HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @Autowired
    private QQOAuth2AccessTokenResponseClient qqOAuth2AccessTokenResponseClient;

    @Autowired
    private QQOAuth2UserService qqOAuth2UserService;

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil ) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 配置 OAuth2 Access Token Response Client
     * 使用自定义的 QQ OAuth2 Client 来处理 JSONP 格式响应
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient defaultClient = new DefaultAuthorizationCodeTokenResponseClient();

        return request -> {
            // 如果是 QQ OAuth，使用自定义的 Client
            if ("qq".equals(request.getClientRegistration().getRegistrationId())) {
                return qqOAuth2AccessTokenResponseClient.getTokenResponse(request);
            }
            // 其他 OAuth 使用默认 Client
            return defaultClient.getTokenResponse(request);
        };
    }

    /**
     * 配置 OAuth2 User Service
     * 使用自定义的 QQ OAuth2 User Service 来处理 QQ 的用户信息获取
     */
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        DefaultOAuth2UserService defaultUserService = new DefaultOAuth2UserService();

        return userRequest -> {
            // 如果是 QQ OAuth，使用自定义的 User Service
            if ("qq".equals(userRequest.getClientRegistration().getRegistrationId())) {
                return qqOAuth2UserService.loadUser(userRequest);
            }
            // 其他 OAuth 使用默认 User Service
            return defaultUserService.loadUser(userRequest);
        };
    }

    // 配置密码加密器 - 使用BCrypt进行密码Hash
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
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
                        .requestMatchers("/api/oauth2/**").permitAll()
                        .requestMatchers("/ws/overview/**").permitAll()
                        .requestMatchers("/api/status/**").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/api/login/oauth2/**").permitAll()
                        .requestMatchers("/api/file/media/validate").hasAuthority("FILE")
                        .requestMatchers("/api/file/media/metadata").hasAuthority("FILE")
                        .requestMatchers("/api/file/media/**").permitAll()
                        .requestMatchers("/api/file/**").hasAuthority("FILE")
                        .requestMatchers("/api/stream/**").hasAuthority("STREAM")
                        .requestMatchers("/api/transcode/**").hasAuthority("TRANSCODE MANAGEMENT")
                        .requestMatchers("/api/ddns/**").hasAuthority("DDNS")
                        .requestMatchers("/api/mail/**").hasAuthority("EMAIL")
                        .requestMatchers("/api/user/**").hasAuthority("USER")
                        .requestMatchers("/api/log/**").hasAuthority("LOG")
                        .requestMatchers("/api/ssh/**").hasAuthority("SSH")
                        .requestMatchers("/api/state/**").authenticated()
                        .requestMatchers("/api/hardware/**").authenticated()
                        .requestMatchers("/api/user-self/**").authenticated()
                        .requestMatchers("/api/monitor/**").authenticated()
                        .anyRequest().permitAll()
                )
                // OAuth 2
                .oauth2Login(
                        oauth2-> oauth2
                                .authorizationEndpoint(authorization ->
                                        authorization
                                                .baseUri("/api/oauth2/authorization")
                                                .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                                )
                                .redirectionEndpoint(redirection ->
                                        redirection.baseUri("/api/login/oauth2/code/*")
                                )
                                .tokenEndpoint(tokenEndpoint ->
                                        tokenEndpoint.accessTokenResponseClient(accessTokenResponseClient())
                                )
                                .userInfoEndpoint(userInfo ->
                                        userInfo.userService(oAuth2UserService())
                                )
                                .successHandler(oAuth2LoginSuccessHandler)
                                .failureHandler(oAuth2LoginFailureHandler)

                )
        ;
        return http.build();
    }

}
