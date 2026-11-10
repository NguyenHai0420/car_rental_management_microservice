package crm.ms.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public JdkClientHttpRequestFactory maintenanceClientHttpRequestFactory(MaintenanceClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }

    @Bean("directRestClientBuilder")
    public RestClient.Builder directRestClientBuilder(JdkClientHttpRequestFactory requestFactory) {
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean("loadBalancedRestClientBuilder")
    @LoadBalanced
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true", matchIfMissing = true)
    public RestClient.Builder loadBalancedRestClientBuilder(JdkClientHttpRequestFactory requestFactory) {
        return RestClient.builder().requestFactory(requestFactory);
    }

    public static RestClient.Builder selectBuilder(
            String baseUrl,
            @Qualifier("directRestClientBuilder") RestClient.Builder directBuilder,
            RestClient.Builder loadBalancedBuilderOrNull) {
        if (loadBalancedBuilderOrNull == null || isDirectHost(baseUrl)) {
            return directBuilder;
        }
        return loadBalancedBuilderOrNull;
    }

    private static boolean isDirectHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String host = URI.create(baseUrl).getHost();
        if (host == null) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
            return true;
        }
        try {
            // Nếu host là địa chỉ IP trực tiếp (ví dụ 10.x.x.x hoặc 192.168.x.x) thì bỏ qua LoadBalancer
            return InetAddress.getByName(host).getHostAddress().equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }
}

