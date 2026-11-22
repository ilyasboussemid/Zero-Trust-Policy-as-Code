package ma.ensias.zerotrust.gateway.config;

import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import ma.ensias.zerotrust.common.mtls.MtlsRestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GatewayConfig {

    @Bean
    public ServiceJwtProvider serviceJwtProvider() {
        return new ServiceJwtProvider("certs/jwt-private.pem", "certs/jwt-public.pem");
    }

    @Bean
    public RestTemplate mtlsRestTemplate(
            @Value("${server.ssl.key-store}") String keyStore,
            @Value("${server.ssl.key-store-password}") String keyStorePassword,
            @Value("${server.ssl.trust-store}") String trustStore,
            @Value("${server.ssl.trust-store-password}") String trustStorePassword) {
        return MtlsRestClientFactory.createMtlsRestTemplate(
                keyStore, keyStorePassword, trustStore, trustStorePassword);
    }
}
