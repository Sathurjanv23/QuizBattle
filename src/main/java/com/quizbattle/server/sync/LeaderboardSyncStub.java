package com.quizbattle.server.sync;

import com.quizbattle.model.LeaderboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Architectural stub for Member 3: Real-Time Score Synchronization & Concurrency Control.
 *
 * <p>Contract Responsibilities for Member 3:
 * <ul>
 *   <li>Atomic score increments and answer timing resolution using {@link AtomicInteger} or atomic CAS.</li>
 *   <li>Broadcasting thread-safe synchronized leaderboard snapshots to room subscribers.</li>
 * </ul>
 * </p>
 */
public class LeaderboardSyncStub {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardSyncStub.class);

    public void recordScore(String roomId, String playerId, int points) {
        log.debug("recordScore stub called: room={}, player={}, points={}", roomId, playerId, points);
    }

    public List<LeaderboardEntry> getSnapshot(String roomId) {
        log.debug("getSnapshot stub called for room={}", roomId);
        return Collections.emptyList();
    }
}
