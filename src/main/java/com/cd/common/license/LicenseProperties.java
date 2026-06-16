package com.cd.common.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "license.rsa")
public class LicenseProperties {

    /**
     * PKCS#8 RSA private key, either PEM text or Base64 body.
     */
    private String privateKey;
}
