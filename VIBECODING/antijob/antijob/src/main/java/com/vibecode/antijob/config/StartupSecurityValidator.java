package com.vibecode.antijob.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class StartupSecurityValidator implements ApplicationRunner {

    private static final String DEV_DEFAULT_JWT_SECRET =
            "dGhpcy1pcy1hLWRldi1vbmx5LXNlY3JldC1rZXktY2hhbmdlLWluLXByb2Q=";

    private final Environment environment;
    private final String jwtSecret;

    public StartupSecurityValidator(Environment environment, @Value("${jwt.secret}") String jwtSecret) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET đang dùng giá trị mặc định dev — BẮT BUỘC set biến môi trường JWT_SECRET riêng khi chạy profile=prod.");
        }
    }
}
