package com.bustracker;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LocationSseService {

    private static class ClientSession {
        private final SseEmitter emitter;
        private final Set<String> trackedBusIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

        public ClientSession(SseEmitter emitter) {
            this.emitter = emitter;
        }

        public SseEmitter getEmitter() { return emitter; }
        public Set<String> getTrackedBusIds() { return trackedBusIds; }
    }

    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String clientId) {
        SseEmitter emitter = new SseEmitter(0L);
        ClientSession session = new ClientSession(emitter);
        clientSessions.put(clientId, session);
        System.out.println("[SSE] Client " + clientId + " subscribed (total clients=" + clientSessions.size() + ")");

        Runnable removeEmitter = () -> {
            clientSessions.remove(clientId);
            System.out.println("[SSE] Client " + clientId + " disconnected (total clients=" + clientSessions.size() + ")");
        };
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(error -> removeEmitter.run());

        return emitter;
    }

    public void trackBus(String clientId, String busId, BusLocationUpdate currentLocation) {
        ClientSession session = clientSessions.get(clientId);
        if (session != null) {
            session.getTrackedBusIds().add(busId);
            System.out.println("[SSE] Client " + clientId + " tracking bus: " + busId);
            if (currentLocation != null) {
                try {
                    send(session.getEmitter(), "location-update", currentLocation);
                } catch (IOException e) {
                    clientSessions.remove(clientId);
                    session.getEmitter().completeWithError(e);
                }
            }
        }
    }

    public void untrackBus(String clientId, String busId) {
        ClientSession session = clientSessions.get(clientId);
        if (session != null) {
            session.getTrackedBusIds().remove(busId);
            System.out.println("[SSE] Client " + clientId + " stopped tracking bus: " + busId);
            if (session.getTrackedBusIds().isEmpty()) {
                System.out.println("[SSE] Client " + clientId + " has no more buses to track. Closing connection.");
                session.getEmitter().complete();
                clientSessions.remove(clientId);
            }
        }
    }

    public void broadcastLocation(BusLocationUpdate update) {
        String busId = update.getBusId();
        clientSessions.forEach((clientId, session) -> {
            if (session.getTrackedBusIds().contains(busId)) {
                try {
                    send(session.getEmitter(), "location-update", update);
                } catch (IOException | IllegalStateException exception) {
                    clientSessions.remove(clientId);
                    session.getEmitter().completeWithError(exception);
                }
            }
        });
    }

    public void broadcastBusStopped(String busId) {
        clientSessions.forEach((clientId, session) -> {
            if (session.getTrackedBusIds().contains(busId)) {
                try {
                    send(session.getEmitter(), "bus-stopped", List.of(new BusStoppedEvent(busId)));
                } catch (IOException | IllegalStateException exception) {
                    clientSessions.remove(clientId);
                    session.getEmitter().completeWithError(exception);
                }
            }
        });
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
        clientSessions.forEach((clientId, session) -> {
            try {
                send(session.getEmitter(), "heartbeat", "keep-alive");
            } catch (IOException | IllegalStateException exception) {
                clientSessions.remove(clientId);
                session.getEmitter().completeWithError(exception);
            }
        });
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
