package com.jesus.system.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaRustJvmBridgeIntegrationTest {

    @Test
    void executesAndComparesSupportedBytecodeInAdaAndRust() throws IOException, InterruptedException {
        assumeTrue(
                System.getenv("JESUS_ADA_VM") != null && System.getenv("JESUS_RUST_VM") != null,
                "Ada and Rust interpreters are only available in the native integration CI job");
        AdaRustJvmBridge bridge = AdaRustJvmBridge.fromEnvironment();
        List<Case> cases = List.of(
                new Case(new byte[] {0x10, 8, 0x10, 5, 0x60, (byte) 0xac}, 13),
                new Case(new byte[] {0x02, 0x10, 3, 0x6c, (byte) 0xac}, 0),
                new Case(new byte[] {0x10, (byte) 0xfd, (byte) 0xac}, -3),
                new Case(new byte[] {0x10, 7, 0x10, 6, 0x68, (byte) 0xac}, 42),
                new Case(new byte[] {0x10, 9, 0x10, 4, 0x64, (byte) 0xac}, 5));

        for (Case testCase : cases) {
            AdaRustJvmBridge.ExecutionResult result = bridge.executeAndCompare(testCase.bytecode());
            assertEquals(testCase.expected(), result.adaResult());
            assertEquals(testCase.expected(), result.rustResult());
        }
    }

    @Test
    void executesStaticMethodExtractedFromClassFileWithAda() throws IOException, InterruptedException {
        assumeTrue(System.getenv("JESUS_ADA_VM") != null, "Ada interpreter is only available in native CI");
        Path classFile = Path.of(
                "target/test-classes/com/jesus/system/jvm/BytecodeFixture.class");
        assertEquals(42, AdaRustJvmBridge.fromEnvironment()
                .executeStaticClassMethod(classFile, "answer", "()I"));
    }

    private record Case(byte[] bytecode, int expected) {
    }
}
