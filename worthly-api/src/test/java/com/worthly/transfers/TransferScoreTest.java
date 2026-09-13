package com.worthly.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.transfers.application.TransferScore;
import org.junit.jupiter.api.Test;

class TransferScoreTest {

    @Test
    void sameDayOwnedHintAutoLinks() {
        int score = TransferScore.score(0, true, false, false);
        assertThat(score).isEqualTo(100);
        assertThat(TransferScore.autoStatus(score)).isEqualTo("LINKED");
    }

    @Test
    void sameDayWithoutHintIsSuggested() {
        int score = TransferScore.score(0, false, false, false);
        assertThat(score).isEqualTo(85);
        assertThat(TransferScore.autoStatus(score)).isEqualTo("SUGGESTED");
    }

    @Test
    void purchaseHintIsSkipped() {
        int score = TransferScore.score(0, false, false, true);
        assertThat(score).isEqualTo(35);
        assertThat(TransferScore.autoStatus(score)).isNull();
    }
}
