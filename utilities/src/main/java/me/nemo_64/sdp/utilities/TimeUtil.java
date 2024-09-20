package me.nemo_64.sdp.utilities;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class TimeUtil {

    public static Optional<String> getInputWithTimeOut(int timeOut, ChronoUnit unit, ExecutorService service) {
        Callable<String> k = () -> new Scanner(System.in).nextLine();
        LocalDateTime start = LocalDateTime.now();
        Future<String> g = service.submit(k);
        while (unit.between(start, LocalDateTime.now()) < timeOut) {
            if (g.isDone()) {
                try {
                    String choice = g.get();
                    return Optional.of(choice);
                } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
                    g = service.submit(k);
                }
            }
        }
        g.cancel(true);
        return Optional.empty();
    }

}
