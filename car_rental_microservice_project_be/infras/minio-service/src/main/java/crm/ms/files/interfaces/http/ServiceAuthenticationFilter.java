package crm.ms.files.interfaces.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.files.application.ClientScope;
import crm.ms.files.infrastructure.config.FileServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    public static final String CLIENT_ATTRIBUTE = ClientScope.class.getName();
    private final FileServiceProperties properties;
    private final ObjectMapper mapper;

    public ServiceAuthenticationFilter(FileServiceProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String name = request.getHeader("X-Service-Name");
        String suppliedKey = request.getHeader("X-Service-Key");
        var client = name == null ? null : properties.clients().get(name);
        if (client == null || suppliedKey == null || !MessageDigest.isEqual(
                client.apiKey().getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            mapper.writeValue(response.getOutputStream(), new ApiError(401, "Service authentication required"));
            return;
        }
        request.setAttribute(CLIENT_ATTRIBUTE, new ClientScope(name, client.buckets(), client.keyPrefix()));
        chain.doFilter(request, response);
    }

    public record ApiError(int code, String message) {
    }
}
