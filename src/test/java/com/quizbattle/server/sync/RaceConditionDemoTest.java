package com.quizbattle.server.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Member 5: the safe side of the live demo must always produce one accepted update.
 * (The unsafe side is not asserted: whether a race shows up on a given run is up to the scheduler.)
 */
@DisplayName("Member 5: RaceConditionDemo safe path")
class RaceConditionDemoTest {

    @RepeatedTest(10)
    @DisplayName("100 latch-released workers through ScoreManager: one accepted, score 10")
    void safeRaceAlwaysOneAccepted() throws Exception {
        RaceConditionDemo.RoundResult r = RaceConditionDemo.raceSafe(100);
        assertEquals(1, r.accepted());
        assertEquals(ScoreManager.POINTS_PER_CORRECT, r.finalScore());
    }
}
