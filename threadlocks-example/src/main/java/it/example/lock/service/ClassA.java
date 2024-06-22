package it.example.lock.service;

import it.example.lock.service.reset.FakeReset;
import it.example.lock.utils.ThreadLocksUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ClassA {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final FakeReset fakeReset;

    private static final Set<String> DEVICE_NAMES = Set.of("LENOVO", "IPHONE", "XBOX");

    public ClassA(FakeReset fakeReset) {
        this.fakeReset = fakeReset;
    }

    /**
     * Inizia il polling che richiama il metodo FakeReset,
     * tenta di acquisire il lock con un timeout.
     */
    @PostConstruct
    public void startPolling() {
        // Avvia il polling
        scheduler.scheduleAtFixedRate(() -> {
            log.info("*********************** START POLLING ************************ ");
            DEVICE_NAMES.forEach(this::fakePoller);
        }, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * Metodo che esegue il polling per un dispositivo specifico.
     *
     * @param deviceName il nome del dispositivo
     */
    private void fakePoller(String deviceName) {
        Thread.startVirtualThread(() -> {
            try {
                log.info("Inizio polling per il dispositivo: " + deviceName);
                // Tenta di acquisire il lock con un timeout
                boolean locked = ThreadLocksUtils.tryLockDeviceWithTimeout(deviceName, 5, TimeUnit.SECONDS);
                if (locked) {
                    try {
                        // Se il lock è stato acquisito allora faccio il reset
                        log.info("Polling reset in esecuzione per: " + deviceName);
                        String result = fakeReset.performFakeReset(deviceName);
                        log.info(result);
                    } finally {
                        ThreadLocksUtils.unlockDeviceSafely(deviceName);
                    }
                } else {
                    // Il lock non è stato acquisito entro il timeout
                    log.info("Impossibile acquisire il lock entro il timeout per: " + deviceName);
                }
            } catch (Exception e) {
                log.error("Errore durante il polling del reset per: " + deviceName, e);
            }
        });
    }
}
