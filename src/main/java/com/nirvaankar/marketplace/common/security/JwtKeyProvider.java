package com.nirvaankar.marketplace.common.security;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Supplies the RS256 keypair used to sign access tokens.
 * <p>
 * Asymmetric signing (not HMAC) is deliberate: when catalog-service and
 * order-service split out of this monolith they will need to verify tokens
 * without holding the signing key. Publishing a public key is safe; sharing an
 * HMAC secret across services is not.
 * <p>
 * If no keys are configured an ephemeral pair is generated. That is fine
 * locally - tokens simply die with the process - and it is refused outright in
 * any profile other than dev/test.
 */
@Slf4j
@Getter
@Component
public class JwtKeyProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtKeyProvider(NirvaankarProperties properties,
                          org.springframework.core.env.Environment environment) {
        String privatePem = properties.security().jwt().privateKeyPem();
        String publicPem = properties.security().jwt().publicKeyPem();

        if (isBlank(privatePem) || isBlank(publicPem)) {
            requireNonProductionProfile(environment);
            log.warn("No JWT keypair configured - generating an ephemeral one. "
                    + "Every restart invalidates all issued access tokens.");
            KeyPair keyPair = generateEphemeralKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
        } else {
            this.privateKey = parsePrivateKey(privatePem);
            this.publicKey = parsePublicKey(publicPem);
        }
    }

    private void requireNonProductionProfile(org.springframework.core.env.Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "staging".equalsIgnoreCase(profile)) {
                throw new IllegalStateException(
                        "NIRVAANKAR_JWT_PRIVATE_KEY and NIRVAANKAR_JWT_PUBLIC_KEY must be set in " + profile);
            }
        }
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate an RSA keypair", e);
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPemArmour(pem));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT private key is not a valid PKCS#8 PEM", e);
        }
    }

    private RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPemArmour(pem));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT public key is not a valid X.509 PEM", e);
        }
    }

    private String stripPemArmour(String pem) {
        return pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
