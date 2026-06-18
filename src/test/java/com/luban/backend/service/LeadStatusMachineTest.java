package com.luban.backend.service;

import com.luban.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lead 状态机单测：合法/非法转移、终态、幂等、解析。
 */
class LeadStatusMachineTest {

    private final LeadStatusMachine machine = new LeadStatusMachine();

    @Test
    void validTransitions() {
        assertThat(machine.canTransit(LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.ASSIGNED)).isTrue();
        assertThat(machine.canTransit(LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.INVALID)).isTrue();
        assertThat(machine.canTransit(LeadStatusMachine.Status.ASSIGNED, LeadStatusMachine.Status.CONTACTING)).isTrue();
        assertThat(machine.canTransit(LeadStatusMachine.Status.CONTACTING, LeadStatusMachine.Status.CONVERTED)).isTrue();
        assertThat(machine.canTransit(LeadStatusMachine.Status.CONTACTING, LeadStatusMachine.Status.LOST)).isTrue();
    }

    @Test
    void invalidTransitionsRejected() {
        assertThat(machine.canTransit(LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.CONTACTING)).isFalse();
        assertThat(machine.canTransit(LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.CONVERTED)).isFalse();
        assertThat(machine.canTransit(LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.LOST)).isFalse();
        assertThat(machine.canTransit(LeadStatusMachine.Status.ASSIGNED, LeadStatusMachine.Status.CONVERTED)).isFalse();
    }

    @Test
    void terminalStatesAllowNoTransition() {
        for (LeadStatusMachine.Status terminal : new LeadStatusMachine.Status[]{
                LeadStatusMachine.Status.CONVERTED, LeadStatusMachine.Status.INVALID, LeadStatusMachine.Status.LOST}) {
            for (LeadStatusMachine.Status any : LeadStatusMachine.Status.values()) {
                if (any == terminal) continue;
                assertThat(machine.canTransit(terminal, any))
                        .as("终态 %s 不可再转移", terminal).isFalse();
            }
        }
    }

    @Test
    void ensureValidAcceptsLegalAndThrowsOnIllegal() {
        assertThatCode(() -> machine.ensureValid(
                LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.ASSIGNED)).doesNotThrowAnyException();

        assertThatThrownBy(() -> machine.ensureValid(
                LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.CONVERTED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_INVALID_TRANSITION");
    }

    @Test
    void sameStateIsIdempotent() {
        assertThatCode(() -> machine.ensureValid(
                LeadStatusMachine.Status.NEW, LeadStatusMachine.Status.NEW)).doesNotThrowAnyException();
    }

    @Test
    void parseAcceptsCaseInsensitive() {
        assertThat(machine.parse("new")).isEqualTo(LeadStatusMachine.Status.NEW);
        assertThat(machine.parse("Contacting")).isEqualTo(LeadStatusMachine.Status.CONTACTING);
        assertThat(machine.parse(null)).isEqualTo(LeadStatusMachine.Status.NEW);
    }

    @Test
    void parseRejectsUnknown() {
        assertThatThrownBy(() -> machine.parse("wontwork"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_INVALID_STATUS");
    }
}
