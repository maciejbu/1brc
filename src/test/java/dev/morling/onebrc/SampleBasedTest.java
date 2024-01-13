package dev.morling.onebrc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleBasedTest {

    public static Stream<Path> test() throws Exception {
        Path samplesDir = Path.of(SampleBasedTest.class.getClassLoader().getResource("samples").toURI());
        return Files.list(samplesDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".txt"));

    }


    @ParameterizedTest
    @MethodSource
    public void test(Path input) throws Exception {
        StringWriter writer = new StringWriter();
        CalculateAverage_maciej.run(input, writer);
        String fileName = input.getFileName().toString();
        Path output = input.getParent().resolve(fileName.substring(0, fileName.lastIndexOf('.')) + ".out");
        assertEquals(Files.readString(output), writer.toString());
    }
}
