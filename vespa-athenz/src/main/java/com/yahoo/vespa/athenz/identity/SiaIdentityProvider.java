// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.joining;

/**
 * @author mortent
 */
public class SiaIdentityProvider extends AbstractComponent implements AthenzIdentityProvider {

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(1);

    private final AthenzDomain domain;
    private final AthenzService service;
    private final String path;
    private final String trustStorePath;
    private final AtomicReference<SSLContext> sslContext = new AtomicReference<SSLContext>();
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    public SiaIdentityProvider(SiaProviderConfig siaProviderConfig) {
        this.domain = new AthenzDomain(siaProviderConfig.athenzDomain());
        this.service = new AthenzService(domain, siaProviderConfig.athenzService());
        this.path = siaProviderConfig.keyPathPrefix();
        this.trustStorePath = siaProviderConfig.trustStorePath();

        sslContext.set(createIdentitySslContext());

        scheduler.scheduleAtFixedRate(this::reloadSslContext, REFRESH_INTERVAL.toMinutes(), REFRESH_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public String getDomain() {
        return domain.getName();
    }

    @Override
    public String getService() {
        return service.getName();
    }

    @Override
    public SSLContext getIdentitySslContext() {
        return sslContext.get();
    }

    private SSLContext createIdentitySslContext() {
        try {
            String certPem = Files.lines(Paths.get(path, "certs", String.format("%s.%s.cert.pem", getDomain(), getService())))
                    .collect(joining());
            X509Certificate certificate = X509CertificateUtils.fromPem(certPem);
            String keyPem = Files.lines(Paths.get(path, "keys", String.format("%s.%s.key.pem", getDomain(), getService())))
                    .collect(joining());
            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(keyPem);

            return new AthenzSslContextBuilder()
                    .withTrustStore(new File(trustStorePath), KeyStoreType.JKS)
                    .withIdentityCertificate(new AthenzIdentityCertificate(certificate, privateKey))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void reloadSslContext() {
        this.sslContext.set(createIdentitySslContext());
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(90, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
