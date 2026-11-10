package crm.ms.config;

import crm.ms.controller.VehicleAccessPolicy;
import crm.ms.controller.VehicleController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class VehicleManagementWebConfig implements WebMvcConfigurer {
    public static final String FLEET_ACTOR = "fleetActor";
    private final VehicleAccessPolicy access;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method && VehicleController.class.isAssignableFrom(method.getBeanType())) {
                    String actor = access.requireFleetManager(request.getHeader("X-User-ID"), request.getHeader("X-User-Roles"));
                    request.setAttribute(FLEET_ACTOR, actor);
                }
                return true;
            }
        });
    }
}
