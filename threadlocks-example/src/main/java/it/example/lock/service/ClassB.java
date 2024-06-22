package it.example.lock.service;

import it.example.lock.service.reset.FakeReset;
import it.example.lock.utils.ThreadLocksUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Esempio di classe con prioritò di acquisizione del Lock
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClassB {


    private final FakeReset fakeReset;


    /**
     * Esempio di un metodo che deve avere la prioritò di nella gestione di Lock
     * per il metodo performFakeReset ( Simuliamo una Fase ) , viene richiamato da una REST API
     * mentre il polling continua sempre a girare
     * @param deviceName il nome del device, chiave della LockMAP
     * @return una Stringa per simulare una risposta del reset
     */

        public String resetDeviceWithPriority(String deviceName) {
            String res = "";
            // se boolean isReset = true significa che ha precedenza.
            ThreadLocksUtils.lockDevice(deviceName, true);
            try {
                log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" );
                log.info("HIGH PRIORITY RESET FOR " + deviceName);
                 res =   fakeReset.performFakeReset(deviceName);
            } finally {
                // RILASCIA IL LOCK E NOTIFICA GLI ALTRI IN ATTESA
                ThreadLocksUtils.unlockDevice(deviceName, true);
            }
            return res;
        }


}
