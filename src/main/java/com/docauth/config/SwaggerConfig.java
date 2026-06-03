package com.docauth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置类
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("文档管理系统 API")
                        .version("1.0.0")
                        .description("文档密码管理和共享系统接口文档")
                        .contact(new Contact()
                                .name("DocAuth Team")
                                .email("support@docauth.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .components(new Components());
    }

    /**
     * 自定义操作处理器，为除了 account/login 之外的所有接口添加 token 参数
     */
    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            // 获取方法名和类名
            String methodName = handlerMethod.getMethod().getName();
            String className = handlerMethod.getBeanType().getSimpleName();

            // 排除 account/login 接口
            if ("AccountController".equals(className) && "login".equals(methodName)) {
                return operation;
            }

            // 添加 token 参数（非必填）
            Parameter tokenParameter = new Parameter()
                    .name("token")
                    .in("header")
                    .description("用户认证令牌（可选）")
                    .required(false);

            // 如果 operation 的 parameters 为 null，需要初始化
            if (operation.getParameters() == null) {
                operation.setParameters(new java.util.ArrayList<>());
            }

            operation.getParameters().add(tokenParameter);

            return operation;
        };
    }
}
