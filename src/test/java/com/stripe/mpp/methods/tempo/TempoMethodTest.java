package com.stripe.mpp.methods.tempo;

import com.stripe.mpp.Mpp;
import com.stripe.mpp.server.MppHandler;
import com.stripe.mpp.server.VerifyResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TempoMethodTest {

    static final TempoMethod METHOD = new TempoMethod("http://rpc.example.com", 1);

    @Test
    void convertsDecimalAmountToAtomicUnits() {
        Map<String, Object> result = METHOD.transformRequest(
            Map.of("amount", "0.010000", "currency", "USDC", "recipient", "0xRecipient")
        );
        assertThat(result.get("amount")).isEqualTo("10000");
    }

    @Test
    void convertsLargerDecimalAmount() {
        Map<String, Object> result = METHOD.transformRequest(
            Map.of("amount", "10.000000", "currency", "USDC", "recipient", "0xRecipient")
        );
        assertThat(result.get("amount")).isEqualTo("10000000");
    }

    @Test
    void injectsMethodDetailsChainId() {
        Map<String, Object> result = METHOD.transformRequest(
            Map.of("amount", "1.000000", "currency", "USDC", "recipient", "0xABC")
        );
        assertThat(result.get("currency")).isEqualTo("USDC");
        assertThat(result.get("recipient")).isEqualTo("0xABC");
        assertThat(result).doesNotContainKey("chain");
        assertThat(((Map<?, ?>) result.get("methodDetails")).get("chainId")).isEqualTo(1);
    }

    @Test
    void rejectsAmountWithTooManyDecimalPlaces() {
        assertThatThrownBy(() -> METHOD.transformRequest(
            Map.of("amount", "0.0000001", "currency", "USDC", "recipient", "0xRecipient")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesMemoIntoMethodDetails() {
        Map<String, Object> result = METHOD.transformRequest(Map.of(
            "amount", "1.000000",
            "currency", "USDC",
            "recipient", "0xABC",
            "memo", "0x" + "ab".repeat(32)
        ));
        assertThat(((Map<?, ?>) result.get("methodDetails")).get("chainId")).isEqualTo(1);
        assertThat(((Map<?, ?>) result.get("methodDetails")).get("memo"))
            .isEqualTo("0x" + "ab".repeat(32));
        assertThat(result.get("memo")).isEqualTo("0x" + "ab".repeat(32));
    }

    @Test
    void builderMemoIsAdvertisedOnTheMethod() {
        String memo = "0x" + "cd".repeat(32);
        TempoMethod method = TempoMethod.custom("http://rpc.example.com", 1).memo(memo).build();
        assertThat(method.memo()).isEqualTo(memo);
    }

    @Test
    void challengeIncludesConfiguredMemo() {
        String memo = "0x" + "ab".repeat(32);
        TempoMethod tempo = TempoMethod.custom("http://rpc.example.com", 1).memo(memo).build();
        MppHandler mpp = Mpp.create(tempo, "api.example.com", "secret");

        VerifyResult result = mpp.charge(null, tempo.chargeIntent(), "1.000000", "USDC", "0xABC");

        Map<String, Object> request = ((VerifyResult.Challenged) result).challenge().request();
        assertThat(request.get("memo")).isEqualTo(memo);
        assertThat(((Map<?, ?>) request.get("methodDetails")).get("memo")).isEqualTo(memo);
    }
}
