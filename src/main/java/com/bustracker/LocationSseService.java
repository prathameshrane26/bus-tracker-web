package com.bustracker;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LocationSseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(Collection<BusLocationUpdate> currentLocations) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        System.out.println("[SSE] Location stream started (active streams=" + emitters.size() + ")");

        Runnable removeEmitter = () -> {
            if (emitters.remove(emitter)) {
                System.out.println("[SSE] Location stream ended (active streams=" + emitters.size() + ")");
            }
        };
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(error -> removeEmitter.run());

        try {
            send(emitter, "location-snapshot", currentLocations);
        } catch (IOException exception) {
            removeEmitter.run();
            emitter.completeWithError(exception);
        }

        return emitter;
    }

    public void broadcastLocation(BusLocationUpdate update) {
        broadcast("location-update", update);
    }

    public void broadcastBusStopped(String busId) {
        broadcast("bus-stopped", List.of(new BusStoppedEvent(busId)));
    }

    private static class BusStoppedEvent {
        private final String busId;
        private final String status = "STOPPED";

        public BusStoppedEvent(String busId) {
            this.busId = busId;
        }

        public String getBusId() { return busId; }
        public String getStatus() { return status; }
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        broadcast("heartbeat", "keep-alive");
    }

    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                send(emitter, eventName, data);
            } catch (IOException | IllegalStateException exception) {
                emitters.remove(emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .reconnectTime(5000)
                    .data(data, MediaType.APPLICATION_JSON));
        }
    }
}
