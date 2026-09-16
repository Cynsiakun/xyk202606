package com.cd.common.license;

import com.cd.entity.LicenseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Component
public class LicenseSigner {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PrivateKey privateKey;

    public LicenseSigner(LicenseProperties properties) {
        this.privateKey = loadPrivateKey(properties.getPrivateKey());
    }

    public String sign(LicenseEntity license) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(buildPayload(license).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign License", e);
        }
    }

    public String buildPayload(LicenseEntity license) {
        return "licenseKey=" + value(license.getLicenseKey()) + '\n'
                + "tenantId=" + value(license.getTenantId()) + '\n'
                + "edition=" + value(license.getEdition()) + '\n'
                + "hostLimit=" + value(license.getHostLimit()) + '\n'
                + "userLimit=" + value(license.getUserLimit()) + '\n'
                + "expireTime=" + (license.getExpireTime() == null ? "" : TIME_FORMATTER.format(license.getExpireTime())) + '\n'
                + "machineId=" + value(license.getMachineId());
    }

    private PrivateKey loadPrivateKey(String configuredPrivateKey) {
        try {
            if (!StringUtils.hasText(configuredPrivateKey)) {
                log.warn("license.rsa.private-key is empty; using an in-memory development RSA key");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                return keyPair.getPrivate();
            }
            String privateKeyText = configuredPrivateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyText);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid license.rsa.private-key", e);
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
