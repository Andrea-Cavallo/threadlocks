# Concetto di Lock nei Threads

Il concetto di lock è fondamentale nella programmazione multithread per gestire l'accesso alle risorse condivise. Un lock aiuta a prevenire che più thread accedano simultaneamente alla stessa risorsa, evitando condizioni di gara e stati inconsistenti. In questo documento, esploreremo come i thread acquisiscono e rilasciano lock, e come questi meccanismi sono implementati e gestiti nel contesto di Java.

## Indice
1. [Introduzione ai Thread](#introduzione-ai-thread)
2. [Monitor-region](#monitor-region)
    - [Acquisizione di un Monitor](#acquisizione-di-un-monitor)
    - [Gestione della Concorrenza](#gestione-della-concorrenza)
    - [Wait Set e Scenario di Bufferizzazione](#wait-set-e-scenario-di-bufferizzazione)
3. [Esempi Pratici con i Lock](#esempi-pratici-con-i-lock)
4. [Utilizzo della Classe `ThreadLocksUtils`](#utilizzo-della-classe-threadlocksutils)
    - [Struttura della Classe](#struttura-della-classe)
    - [Metodi Principali](#metodi-principali)

## Introduzione ai Thread
Un thread è una sequenza di istruzioni eseguite in modo indipendente all'interno del processo di un programma. La programmazione multithread permette a più thread di eseguire concorrentemente, migliorando l'efficienza dell'applicazione.

## Monitor-region

### Acquisizione di un Monitor
Quando un thread desidera acquisire un monitor (un tipo di lock), segue questi passi:
- **Entra nel EntrySet**: Controlla se il monitor è già in possesso di un altro thread.
- **Acquisizione del monitor**: Se il monitor è libero, il thread lo acquisisce.
- **Rilascio del monitor**: Una volta completate le operazioni necessarie, il thread rilascia il monitor e esce.

### Gestione della Concorrenza
Se il monitor è occupato, il thread entrante deve attendere. La competizione per acquisire un monitor libero dipende dalla politica di scheduling del thread della JVM, che può essere LIFO (Last In, First Out), FIFO (First In, First Out), o altre.

### Wait Set e Scenario di Bufferizzazione
- **Write Thread**: Gestisce la scrittura in una zona di memoria.
- **Read Thread**: Gestisce la lettura dalla stessa zona di memoria.
- **Operazioni di wait e notify**: Gestiscono la sincronizzazione tra i thread, segnalando la disponibilità di risorse o l'attesa per il loro rilascio.

## Esempi Pratici con i Lock
Nella sezione successiva, forniremo esempi pratici di come utilizzare i lock in Java per gestire la sincronizzazione tra thread, illustrando con codice esemplificativo il corretto utilizzo di monitor, wait, e notify per risolvere problemi di concorrenza.
# Gestione della Priorità di Lock nei Threads

Questo esempio dimostra come implementare una gestione della priorità per l'acquisizione di lock tra due classi, `ClassA` e `ClassB`, dove `ClassA` esegue un polling continuo e `ClassB` deve avere la priorità nell'acquisizione del lock quando chiamata tramite una REST API.

## ClassA

`ClassA` è responsabile di eseguire un polling continuo su un dispositivo. Utilizza un `ScheduledExecutorService` per tentare di acquisire un lock su un dispositivo a intervalli regolari. Se il lock viene acquisito entro un timeout specificato, esegue un'operazione di reset.

### Esempio di Codice per ClassA

```java

@Slf4j
@Component
public class ClassA {

   private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
   private final FakeReset fakeReset;

   // Set di costanti dei dispositivi
   private static final Set<String> DEVICE_NAMES = Set.of("PRINTER", "IPHONE", "XBOX");

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
```

## ClassB
`ClassB` è responsabile di eseguire un'operazione di reset su un dispositivo, avendo la priorità nell'acquisizione del lock. Questa classe viene chiamata tramite una REST API e deve acquisire il lock immediatamente, sospendendo qualsiasi altra operazione in corso sul dispositivo.

### Esempio di Codice per ClassB

```java
/**
* Esempio di classe con priorità di acquisizione del Lock
  */
  @Service
  @Slf4j
  @RequiredArgsConstructor
  public class ClassB {

  private final FakeReset fakeReset;

  /**
    * Esempio di un metodo che deve avere la priorità nella gestione di Lock
    * per il metodo performFakeReset (Simuliamo una Fase), viene richiamato da una REST API
    * mentre il polling continua sempre a girare.
    * @param deviceName il nome del device, chiave della LockMAP
    * @return una Stringa per simulare una risposta del reset
      */
      public String resetDeviceWithPriority(String deviceName) {
      String res = "";
      ThreadLocksUtils.lockDevice(deviceName, true); // Priorità alta per il reset
      try {
          log.info("Reset prioritario in esecuzione per: " + deviceName);
          res = fakeReset.performFakeReset(deviceName);
      } finally {
          ThreadLocksUtils.unlockDevice(deviceName, true);
            }
        return res;
        }
      }
```

## Utilizzo della Classe `ThreadLocksUtils`

### Struttura della Classe
La classe `ThreadLocksUtils` fornisce una serie di utilità per gestire i lock nei thread in Java. Utilizza `ConcurrentHashMap` per garantire la thread-safety durante le operazioni di lettura e scrittura. Qui di seguito è riportata una descrizione dettagliata dei principali componenti della classe.

```java
@Slf4j
public class ThreadLocksUtils {
    private static final ConcurrentMap<String, LockInfo> LOCK_MAP = new ConcurrentHashMap<>();

    private static class LockInfo {
        final ReentrantLock lock = new ReentrantLock();
        final Condition resetPriority = lock.newCondition();
        boolean isResetInProgress = false;
    }

    // Metodi principali
    public static void lockDevice(String deviceName, boolean isReset) { ... }
    public static void unlockDevice(String deviceName, boolean isReset) { ... }
    public static boolean tryLockDeviceWithTimeout(String deviceName, long timeout, TimeUnit unit) { ... }
    public static void unlockDeviceSafely(String deviceName) { ... }

    // Metodi ausiliari
    private static LockInfo getLockInfo(String deviceName) { ... }
    private static void tryRemoveLock(String deviceName) { ... }
}
```

# Dettagli dell'oggetto `LockInfo`

L'oggetto `LockInfo` è una classe interna statica che rappresenta le informazioni di lock per un dispositivo specifico. È utilizzato per gestire l'accesso concorrente ai dispositivi, assicurando che le operazioni critiche abbiano la priorità quando necessario. Di seguito sono riportati i componenti principali della classe `LockInfo` e una spiegazione dettagliata del loro utilizzo.

## Componenti di `LockInfo`

```java
private static class LockInfo {
    final ReentrantLock lock = new ReentrantLock();
    final Condition resetPriority = lock.newCondition();
    boolean isResetInProgress = false;
}
```

### ReentrantLock lock
- Descrizione: ReentrantLock è una classe che implementa un lock ri-entrante, il che significa che il thread che detiene il lock può riacquisirlo più volte senza rimanere bloccato. È utilizzato per controllare l'accesso esclusivo a una risorsa (in questo caso, un dispositivo specifico).
- Funzione: Permette di evitare problemi di concorrenza, come le condizioni di gara, assicurando che solo un thread alla volta possa eseguire un'operazione protetta da questo lock.

### Condition resetPriority
- Descrizione: Condition è un'interfaccia che fornisce un meccanismo di comunicazione tra i thread. È associata al ReentrantLock e permette a un thread di sospendersi (mettendosi in attesa) fino a quando un'altra condizione non viene soddisfatta (in questo caso, fino a quando il reset del dispositivo non è completato).
- Funzione: resetPriority è utilizzata per gestire la priorità di reset. Quando un thread richiede un reset con priorità, può utilizzare questa condizione per sospendere altri thread finché l'operazione di reset non è completata.

### boolean isResetInProgress
- Descrizione: Questo flag booleano indica se un reset del dispositivo è attualmente in corso.
- Funzione: Se isResetInProgress è true, significa che il dispositivo è in fase di reset e altri thread devono attendere fino a quando il reset è completato per poter acquisire il lock.

