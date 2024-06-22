package it.example.lock.service.reset;

import org.springframework.stereotype.Component;

@Component
public class FakeReset {

    /**
     * Simula un'operazione di reset che termina dopo 30 secondi
     * per l'XBOX e rapidamente per gli altri dispositivi.
     * Restituisce un risultato randomico tra "OK" e "NO OK".
     *
     * @param name il nome del dispositivo
     * @return String "OK" o "NO OK" in modo randomico.
     */
    public String performFakeReset(String name) {
        try {
            if ("XBOX".equalsIgnoreCase(name)) {
                // Simula un'operazione lunga (es. reset) per l'XBOX
                Thread.sleep(30000);
            } else {
                // Simula un'operazione breve per altri dispositivi
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "RESET OF (" + name + ") WAS UNKNOWN";
        }

        if (Math.random() < 0.5) {
            return "RESET OF (" + name + ") WAS SUCCESSFULLY";
        } else {
            return "RESET OF (" + name + ") WAS NO OK";
        }
    }
}
