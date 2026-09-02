package cn.net.pap.example.proguard.config;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 多租户
 */
@Component
public class PapTenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    private static final Logger logger = LoggerFactory.getLogger(PapTenantIdentifierResolver.class);

    private static final String DEFAULT_TENANT_ID = "default";

    @Override
    public String resolveCurrentTenantIdentifier() {
        try {
            if(RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
                String papTenantId = request.getHeader("pap-tenant-id");
                if(papTenantId != null && !"".equals(papTenantId)) {
                    return papTenantId;
                }
            }
        } catch (IllegalStateException e) {
            logger.error("resolveCurrentTenantIdentifier", e);
            // for un web environment
        }
        return DEFAULT_TENANT_ID;
    }

    @Override

    public boolean validateExistingCurrentSessions() {
        return true;
    }

}