package com.stripe.mpp.methods.tempo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributionTest {

    @Test
    void tagMatchesKeccakVector() {
        assertThat(Attribution.TAG_HEX).isEqualTo("0xef1ed712");
    }

    @Test
    void encodeReturns32ByteHex() {
        String memo = Attribution.encode("api.example.com", "chal-id");
        assertThat(memo).matches("0x[0-9a-f]{64}");
        assertThat(memo.length()).isEqualTo(66);
    }

    @Test
    void encodeStartsWithTagAndVersion() {
        String memo = Attribution.encode("api.example.com", "chal-id");
        assertThat(memo.substring(0, 10)).isEqualTo(Attribution.TAG_HEX);
        assertThat(memo.substring(10, 12)).isEqualTo("01");
    }

    @Test
    void encodeIsDeterministicForTheSameChallenge() {
        String a = Attribution.encode("api.example.com", "challenge-a");
        String b = Attribution.encode("api.example.com", "challenge-a");
        String c = Attribution.encode("api.example.com", "challenge-b");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void isMppMemo() {
        assertThat(Attribution.isMppMemo(Attribution.encode("test-server", "chal-123"))).isTrue();
        assertThat(Attribution.isMppMemo("0x" + "00".repeat(32))).isFalse();
        assertThat(Attribution.isMppMemo("0x1234")).isFalse();
        assertThat(Attribution.isMppMemo("")).isFalse();
        assertThat(Attribution.isMppMemo(null)).isFalse();
    }

    @Test
    void verifyServer() {
        String memo = Attribution.encode("test-server", "chal-123");
        assertThat(Attribution.verifyServer(memo, "test-server")).isTrue();
        assertThat(Attribution.verifyServer(memo, "other-server")).isFalse();
    }

    @Test
    void verifyChallengeBinding() {
        String memo = Attribution.encode("test-server", "chal-123");
        assertThat(Attribution.verifyChallengeBinding(memo, "chal-123")).isTrue();
        assertThat(Attribution.verifyChallengeBinding(memo, "chal-b")).isFalse();
        assertThat(Attribution.verifyChallengeBinding(memo, null)).isFalse();
    }

    @Test
    void decodeRoundtripWithClientId() {
        String memo = Attribution.encode("test-server", "chal-123", "test-client");
        Attribution.Decoded decoded = Attribution.decode(memo);

        assertThat(decoded).isNotNull();
        assertThat(decoded.version()).isEqualTo(1);
        assertThat(decoded.serverFingerprint()).startsWith("0x").hasSize(22);
        assertThat(decoded.clientFingerprint()).isNotNull().startsWith("0x").hasSize(22);
        assertThat(decoded.nonce()).startsWith("0x").hasSize(16);
    }

    @Test
    void decodeWithoutClientIsAnonymous() {
        Attribution.Decoded decoded = Attribution.decode(Attribution.encode("test-server", "chal-123"));
        assertThat(decoded).isNotNull();
        assertThat(decoded.clientFingerprint()).isNull();
    }

    @Test
    void decodeInvalidMemo() {
        assertThat(Attribution.decode("0x" + "00".repeat(32))).isNull();
    }

    @Test
    void differentServerIdsProduceDifferentFingerprints() {
        Attribution.Decoded a = Attribution.decode(Attribution.encode("server-a", "chal"));
        Attribution.Decoded b = Attribution.decode(Attribution.encode("server-b", "chal"));
        assertThat(a.serverFingerprint()).isNotEqualTo(b.serverFingerprint());
    }

    @Test
    void fingerprintMatchesKeccakVector() {
        Attribution.Decoded decoded = Attribution.decode(Attribution.encode("test-server", "chal-123"));
        assertThat(decoded.serverFingerprint()).isEqualTo("0x3c224683515d3cb375bf");
    }

    @Test
    void acceptsMemoProducedByOtherSdks() {
        String memo = "0xef1ed712013c224683515d3cb375bf000000000000000000003b2941df281c7c";
        assertThat(Attribution.isMppMemo(memo)).isTrue();
        assertThat(Attribution.verifyServer(memo, "test-server")).isTrue();
        assertThat(Attribution.verifyChallengeBinding(memo, "chal-123")).isTrue();
    }
}
