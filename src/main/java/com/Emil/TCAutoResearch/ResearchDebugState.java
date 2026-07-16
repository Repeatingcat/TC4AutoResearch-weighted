package com.Emil.TCAutoResearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResearchDebugState {

    public enum Status {
        IDLE,
        RUNNING,
        SUCCESS,
        NO_SOLUTION,
        ERROR
    }

    public static final class CostEntry {

        public final String tag;
        public final int count;
        public final int unitCost;
        public final long subtotal;

        private CostEntry(String tag, int count, int unitCost) {
            this.tag = tag;
            this.count = count;
            this.unitCost = unitCost;
            this.subtotal = (long) count * unitCost;
        }
    }

    public static final class Snapshot {

        public final Status status;
        public final int noteId;
        public final long startedAt;
        public final long finishedAt;
        public final long totalCost;
        public final String solution;
        public final String message;
        public final List<CostEntry> entries;
        public final String searchMode;
        public final boolean optimal;
        public final int expandedStates;
        public final int generatedStates;
        public final int candidateSolutions;

        private Snapshot(Status status, int noteId, long startedAt, long finishedAt, long totalCost, String solution,
            String message, List<CostEntry> entries, String searchMode, boolean optimal, int expandedStates,
            int generatedStates, int candidateSolutions) {
            this.status = status;
            this.noteId = noteId;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.totalCost = totalCost;
            this.solution = solution;
            this.message = message;
            this.entries = Collections.unmodifiableList(entries);
            this.searchMode = searchMode;
            this.optimal = optimal;
            this.expandedStates = expandedStates;
            this.generatedStates = generatedStates;
            this.candidateSolutions = candidateSolutions;
        }

        public long getElapsedMillis() {
            long end = finishedAt == 0 ? System.currentTimeMillis() : finishedAt;
            return startedAt == 0 ? 0 : Math.max(0, end - startedAt);
        }
    }

    private static Map<String, Integer> activeCosts = new LinkedHashMap<>();
    private static Snapshot snapshot = emptySnapshot();

    private ResearchDebugState() {}

    public static synchronized void begin(int noteId, Map<String, Integer> costs) {
        activeCosts = new LinkedHashMap<>(costs);
        long now = System.currentTimeMillis();
        snapshot = new Snapshot(
            Status.RUNNING,
            noteId,
            now,
            0,
            0,
            "",
            "",
            new ArrayList<CostEntry>(),
            "",
            false,
            0,
            0,
            0);
    }

    public static synchronized void recordSearchStats(String line) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : line.split("\\|")) {
            int separator = part.indexOf('=');
            if (separator > 0 && separator < part.length() - 1)
                values.put(part.substring(0, separator), part.substring(separator + 1));
        }

        snapshot = new Snapshot(
            snapshot.status,
            snapshot.noteId,
            snapshot.startedAt,
            snapshot.finishedAt,
            snapshot.totalCost,
            snapshot.solution,
            snapshot.message,
            new ArrayList<>(snapshot.entries),
            values.getOrDefault("mode", ""),
            Boolean.parseBoolean(values.getOrDefault("optimal", "false")),
            parseInt(values.get("expanded")),
            parseInt(values.get("generated")),
            parseInt(values.get("candidates")));
    }

    public static synchronized void recordSolution(String line) {
        if (line == null || line.trim()
            .isEmpty()) return;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String item : line.split("&")) {
            int separator = item.lastIndexOf('|');
            if (separator < 0 || separator == item.length() - 1) continue;
            String tag = item.substring(separator + 1);
            Integer count = counts.get(tag);
            counts.put(tag, count == null ? 1 : count + 1);
        }

        List<CostEntry> entries = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Integer configuredCost = activeCosts.get(entry.getKey());
            int unitCost = configuredCost == null ? 16 : configuredCost;
            CostEntry costEntry = new CostEntry(entry.getKey(), entry.getValue(), unitCost);
            entries.add(costEntry);
            total += costEntry.subtotal;
        }
        entries.sort(Comparator.comparingLong((CostEntry entry) -> entry.subtotal)
            .reversed()
            .thenComparing(entry -> entry.tag));

        long startedAt = snapshot.startedAt == 0 ? System.currentTimeMillis() : snapshot.startedAt;
        snapshot = new Snapshot(
            Status.SUCCESS,
            snapshot.noteId,
            startedAt,
            System.currentTimeMillis(),
            total,
            line,
            "",
            entries,
            snapshot.searchMode,
            snapshot.optimal,
            snapshot.expandedStates,
            snapshot.generatedStates,
            snapshot.candidateSolutions);
    }

    public static synchronized void recordNoSolution(int exitCode) {
        if (snapshot.status == Status.SUCCESS) return;
        snapshot = completed(Status.NO_SOLUTION, "Solver exit code: " + exitCode);
    }

    public static synchronized void recordFailure(Throwable error) {
        String message = error == null || error.getMessage() == null ? "Unknown solver error" : error.getMessage();
        snapshot = completed(Status.ERROR, message);
    }

    public static synchronized void recordFailure(String message) {
        snapshot = completed(Status.ERROR, message == null ? "Unknown solver error" : message);
    }

    public static synchronized Snapshot getSnapshot() {
        return snapshot;
    }

    private static Snapshot completed(Status status, String message) {
        long startedAt = snapshot.startedAt == 0 ? System.currentTimeMillis() : snapshot.startedAt;
        return new Snapshot(
            status,
            snapshot.noteId,
            startedAt,
            System.currentTimeMillis(),
            snapshot.totalCost,
            snapshot.solution,
            message,
            new ArrayList<>(snapshot.entries),
            snapshot.searchMode,
            snapshot.optimal,
            snapshot.expandedStates,
            snapshot.generatedStates,
            snapshot.candidateSolutions);
    }

    private static Snapshot emptySnapshot() {
        return new Snapshot(
            Status.IDLE,
            0,
            0,
            0,
            0,
            "",
            "",
            new ArrayList<CostEntry>(),
            "",
            false,
            0,
            0,
            0);
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
