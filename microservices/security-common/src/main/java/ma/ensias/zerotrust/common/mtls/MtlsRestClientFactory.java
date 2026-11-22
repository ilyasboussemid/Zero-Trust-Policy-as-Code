package ma.ensias.zerotrust.common.mtls;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Construit un client HTTP RestTemplate configure en Mutual TLS (mTLS) :
 * presente le keystore du microservice appelant et verifie le serveur via le truststore de la CA.
 */
public final class MtlsRestClientFactory {

    private MtlsRestClientFactory() {}

    public static RestTemplate createMtlsRestTemplate(String keyStorePath,
                                                      String keyStorePassword,
                                                      String trustStorePath,
                                                      String trustStorePassword) {
        try {
            KeyStore keyStore = loadPkcs12(keyStorePath, keyStorePassword);
            KeyStore trustStore = loadPkcs12(trustStorePath, trustStorePassword);

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(keyStore, keyStorePassword.toCharArray())
                    .loadTrustMaterial(trustStore, null)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .build();

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        } catch (Exception e) {
            throw new IllegalStateException("Echec d'initialisation du client HTTP mTLS", e);
        }
    }

    private static KeyStore loadPkcs12(String classpathLocation, String password) throws Exception {
        String cleanPath = classpathLocation.replace("classpath:", "");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = new ClassPathResource(cleanPath).getInputStream()) {
            ks.load(is, password.toCharArray());
        }
        return ks;
    }
}
