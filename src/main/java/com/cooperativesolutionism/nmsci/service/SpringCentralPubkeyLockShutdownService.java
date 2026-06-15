package com.cooperativesolutionism.nmsci.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SpringCentralPubkeyLockShutdownService implements CentralPubkeyLockShutdownService {

    private static final Logger logger = LoggerFactory.getLogger(SpringCentralPubkeyLockShutdownService.class);

    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public SpringCentralPubkeyLockShutdownService(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            logger.warn("中心公钥冻结停机请求已提交，忽略重复请求");
            return;
        }

        Thread shutdownThread = new Thread(
                () -> SpringApplication.exit(applicationContext, () -> 0),
                "central-pubkey-lock-shutdown"
        );
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
