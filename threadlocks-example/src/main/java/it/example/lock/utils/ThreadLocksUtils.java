package it.example.lock.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Classe di utility per la gestione dei thread locks sui devices

 * @author Andrea.Cavallo
 */

@Slf4j
public class ThreadLocksUtils {
    /**
     * Mappa concorrente che mantiene le associazioni tra i nomi dei dispositivi e le loro informazioni di lock.
     * Utilizza {@link ConcurrentHashMap} per garantire la thread-safety durante le operazioni di lettura e scrittura.
     */
    private static final ConcurrentMap<String, LockInfo> LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * Classe interna che rappresenta le informazioni di lock per un dispositivo.
     * Contiene un {@link ReentrantLock} per controllare l'accesso al dispositivo, una {@link Condition}
     * per gestire la priorità di reset e un flag booleano per indicare se un reset è in corso.
     *
     * <p>Il lock {@code ReentrantLock} permette di acquisire e rilasciare il controllo in modo esclusivo,
     * mentre la condition {@code resetPriority} può essere utilizzata per mettere in pausa l'esecuzione
     * di thread che tentano di acquisire il lock durante un'operazione di reset, garantendo che il reset
     * abbia la priorità sull'accesso normale.</p>
     *
     * <p>Il flag {@code isResetInProgress} serve a indicare se il dispositivo è attualmente soggetto a un reset.</p>
     */
    private static class LockInfo {
        final ReentrantLock lock = new ReentrantLock();
        final Condition resetPriority = lock.newCondition();
        boolean isResetInProgress = false;
    }



    /**
     * Blocca un dispositivo specificato dal nome. Questo metodo garantisce che se un reset è in corso,
     * ogni ulteriore tentativo di bloccare il dispositivo sarà messo in attesa finché il reset non viene completato.
     * Se il parametro {@code isReset} è impostato su {@code true}, il metodo segnala che un reset è iniziato
     * e impedisce l'accesso al dispositivo da parte di altri thread finché il reset non è concluso.
     * In caso di interruzione durante l'attesa, il thread corrente viene interrotto e viene loggato un errore.
     *
     * @param deviceName Il nome del dispositivo da bloccare.
     * @param isReset Se {@code true}, indica che sta iniziando un reset del dispositivo, altrimenti indica
     *                una normale operazione di blocco senza iniziare un reset.
     * @throws InterruptedException Se il thread corrente è interrotto mentre è in attesa che il reset del
     * dispositivo sia completato.
     */
    public static void lockDevice(String deviceName, boolean isReset) {
        LockInfo lockInfo = getLockInfo(deviceName);
        lockInfo.lock.lock();
        try {
            if (isReset) {
                while (lockInfo.isResetInProgress) {
                    lockInfo.resetPriority.await();
                }
                lockInfo.isResetInProgress = true;
            } else {
                while (lockInfo.isResetInProgress) {
                    lockInfo.resetPriority.await();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interruzione durante l'attesa del lock sul dispositivo: " + deviceName, e);
        } finally {
            if (!isReset) {
                lockInfo.lock.unlock();
            }
        }
    }

    /**
     * Sblocca un dispositivo specificato dal nome. Se il parametro {@code isReset} è {@code true},
     * segnala la fine di un reset del dispositivo e risveglia tutti i thread in attesa di poter accedere al dispositivo.
     * Dopo aver sbloccato il dispositivo, tenta di rimuovere il {@link LockInfo} associato dal sistema di gestione dei lock,
     * se nessun altro thread sta attualmente tenendo il lock. Questo aiuta a mantenere pulita la mappa dei lock
     * evitando di occupare memoria inutilmente.
     * Il dispositivo verrà sbloccato solo se il lock è attualmente detenuto dal thread chiamante,
     * evitando così azioni non autorizzate da thread che non detengono il lock.
     *
     * @param deviceName Il nome del dispositivo da sbloccare.
     * @param isReset Se {@code true}, indica che un reset del dispositivo è stato completato,
     *                e tutti i thread in attesa per il reset possono essere risvegliati.
     */

    public static void unlockDevice(String deviceName, boolean isReset) {
        LockInfo lockInfo = LOCK_MAP.get(deviceName);
        if (lockInfo != null && lockInfo.lock.isHeldByCurrentThread()) {
            if (isReset) {
                lockInfo.isResetInProgress = false;
                lockInfo.resetPriority.signalAll();
            }
            lockInfo.lock.unlock();
            tryRemoveLock(deviceName);
        }
    }


    /**
     * Tenta di acquisire il lock su un dispositivo specificato dal nome, attendendo per un periodo di tempo
     * definito prima di rinunciare. Questo metodo utilizza un timeout per evitare di attendere indefinitamente
     * il lock in caso di alta contesa o se il dispositivo è frequentemente bloccato per lunghi periodi.
     * Se il lock viene acquisito con successo, ma viene rilevato che un reset del dispositivo è in corso,
     * il lock viene immediatamente rilasciato e il metodo restituisce {@code false}.
     *
     * @param deviceName Il nome del dispositivo per cui tentare di acquisire il lock.
     * @param timeout Il tempo massimo da attendere per acquisire il lock.
     * @param unit L'unità di tempo del timeout (es. {@link TimeUnit#SECONDS}, {@link TimeUnit#MILLISECONDS}).
     * @return {@code true} se il lock è stato acquisito con successo e non è in corso un reset del dispositivo,
     *         altrimenti {@code false}.
     * @throws InterruptedException Se il thread corrente è interrotto mentre attende di acquisire il lock.
     */
    public static boolean tryLockDeviceWithTimeout(String deviceName, long timeout, TimeUnit unit) {
        LockInfo lockInfo = getLockInfo(deviceName);
        try {
            if (lockInfo.lock.tryLock(timeout, unit)) {
                log.info("Lock acquisito sul dispositivo con timeout: " + deviceName);
                // Dopo aver acquisito il lock, verifica lo stato di reset
                try {
                    if (lockInfo.isResetInProgress) {
                        // Se c'è un reset in corso, rilascia e segnala il fallimento
                        lockInfo.lock.unlock();
                        return false;
                    }
                    return true; // Lock acquisito con successo
                } catch (Exception e) {
                    lockInfo.lock.unlock();
                    throw e;
                }
            } else {
                log.info("Timeout nell'acquisizione del lock sul dispositivo: " + deviceName);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interruzione durante l'attesa del lock sul dispositivo: " + deviceName, e);
            return false;
        }
    }

    /**
     * Rilascia in modo sicuro il lock su un dispositivo specificato dal nome, se il lock è attualmente detenuto
     * dal thread che invoca questo metodo. Dopo aver rilasciato il lock, tenta di rimuovere il {@link LockInfo}
     * associato al dispositivo dalla mappa dei lock, se le condizioni lo permettono. Questo metodo garantisce che
     * il lock venga rilasciato solo se detenuto dal thread corrente, evitando così il rilascio improprio del lock.
     * Viene inoltre loggata un'informazione che conferma il rilascio sicuro del lock.
     * Questa procedura aiuta a mantenere l'integrità e la sicurezza nell'accesso ai dispositivi, assicurando che
     * solo i thread che hanno effettivamente acquisito il lock possano rilasciarlo, e tentando di rimuovere le
     * informazioni di lock non più necessarie per ottimizzare l'uso della memoria.
     *
     * @param deviceName Il nome del dispositivo per cui si intende rilasciare il lock.
     */
    public static void unlockDeviceSafely(String deviceName) {
        LockInfo lockInfo = LOCK_MAP.get(deviceName);
        if (lockInfo != null && lockInfo.lock.isHeldByCurrentThread()) {
            lockInfo.lock.unlock();
            log.info("Lock rilasciato in modo sicuro sul dispositivo: " + deviceName);
            tryRemoveLock(deviceName);
        }
    }



    /**
     * Restituisce un'istanza di {@link LockInfo} associata al nome del dispositivo specificato.
     * Se non esiste un'istanza di {@link LockInfo} per il nome del dispositivo fornito,
     * il metodo ne crea una nuova, la aggiunge alla mappa {@code LOCK_MAP} e la restituisce.
     * L'accesso a {@code LOCK_MAP} è sincronizzato per garantire la thread-safety.
     *
     * @param deviceName Il nome del dispositivo per cui si desidera ottenere l'informazione di blocco.
     * @return Un'istanza di {@link LockInfo} associata al nome del dispositivo fornito.
     */
    private static LockInfo getLockInfo(String deviceName) {
        synchronized (LOCK_MAP) {
            LockInfo lockInfo = LOCK_MAP.get(deviceName);
            if (lockInfo == null) {
                lockInfo = new LockInfo();
                LOCK_MAP.put(deviceName, lockInfo);
            }
            return lockInfo;
        }
    }

    /**
     * Tenta di rimuovere il lock associato a un dispositivo specificato dal nome.
     * Questa operazione viene effettuata solo se nessun reset è in corso per il dispositivo e
     * non ci sono thread in attesa di ottenere il lock. Il tentativo di acquisizione del lock
     * viene effettuato per assicurarsi che nessun altro thread stia operando sul dispositivo
     * al momento della rimozione. Se il lock viene acquisito con successo e le condizioni
     * per la rimozione sono soddisfatte, il lock viene rimosso dalla mappa dei lock e viene
     * loggata un'informazione sull'azione effettuata.
     * Questo metodo aiuta a prevenire perdite di memoria rimuovendo le istanze di {@link LockInfo}
     * che non sono più necessarie, mantenendo così la mappa dei lock pulita e contenuta.
     *
     * @param deviceName Il nome del dispositivo per cui si tenta di rimuovere il lock.
     */

    private static void tryRemoveLock(String deviceName) {
        LockInfo lockInfo = LOCK_MAP.get(deviceName);
        if (lockInfo != null && lockInfo.lock.tryLock()) {
            try {
                if (!lockInfo.isResetInProgress && lockInfo.lock.getQueueLength() == 0) {
                    LOCK_MAP.remove(deviceName);
                    log.info("Lock rimosso per il dispositivo: " + deviceName);
                }
            } finally {
                lockInfo.lock.unlock();
            }
        }
    }
}