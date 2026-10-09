package bench;

import com.ctc.wstx.stax.WstxInputFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.dataformat.xml.XmlMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Unmarshal / marshal throughput of one implementation on one fixture.
 *
 * <ul>
 *   <li>{@code impl}: a jaxb-ri build under {@code variants/} (e.g. {@code master}, a branch name,
 *       {@code combined}) or {@code jackson}.</li>
 *   <li>{@code fixture}: {@code catalog4|catalog32|catalog256} (attribute + text, 191 B / 1,407 B /
 *       11,575 B, identical to the simdxml-java ObjectBindingGcBenchmark documents),
 *       {@code rows32} (4 typed child elements per row, unqualified) and {@code nsrows32}
 *       (the same with a default namespace).</li>
 *   <li>{@code parser}: for JAXB unmarshal, {@code sax} = {@code unmarshal(InputStream)} through the
 *       JDK's JAXP SAX parser (the JAXB default), {@code stax} = {@code unmarshal(XMLStreamReader)}
 *       over Woodstox, the StAX parser Jackson XML itself uses (and the CXF/JAX-WS path).
 *       Jackson always reads bytes with its own Woodstox stack.
 *       {@code sax-woodstox} = {@code unmarshal(InputStream)} with the system property
 *       {@code javax.xml.parsers.SAXParserFactory} pointing at Woodstox's SAX factory: the
 *       zero-code-change configuration, checked for the parser actually used and for DOCTYPE
 *       rejection before measuring.</li>
 * </ul>
 *
 * The setup verifies that the bound / written graph is identical across implementations, so no
 * implementation can be timed while silently doing less work.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2", "-Xms1g", "-Xmx1g"})
@State(Scope.Benchmark)
public class BindingBenchmark {
    @Param({"baseline"})
    public String impl;

    @Param({"catalog32"})
    public String fixture;

    @Param({"unmarshal"})
    public String op;

    @Param({"sax"})
    public String parser;

    private Object source;
    private Class<?> type;
    private byte[] xml;
    private Unmarshaller unmarshaller;
    private Marshaller marshaller;
    private XMLInputFactory stax;
    private XmlMapper jackson;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        source = switch (fixture) {
            case "catalog4" -> Models.catalog(4);
            case "catalog32" -> Models.catalog(32);
            case "catalog256" -> Models.catalog(256);
            case "rows32" -> Models.fieldRows(32);
            case "nsrows32" -> Models.namespacedRows(32);
            default -> throw new IllegalArgumentException(fixture);
        };
        type = source.getClass();
        // The document is always produced by the same reference writer (the variant under test is
        // irrelevant for that: all jaxb-ri builds must agree), so every impl reads identical bytes.
        JAXBContext reference = Impl.context(System.getProperty("bench.reference", "baseline"), type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reference.createMarshaller().marshal(source, out);
        xml = out.toByteArray();
        System.out.println("# fixture " + fixture + " = " + xml.length + " bytes, impl=" + impl
                + ", java=" + System.getProperty("java.version"));

        if ("sax-woodstox".equals(parser))
            System.setProperty("javax.xml.parsers.SAXParserFactory", "com.ctc.wstx.sax.WstxSAXParserFactory");
        if (Impl.isJackson(impl)) {
            jackson = new XmlMapper();
        } else {
            JAXBContext context = Impl.context(impl, type);
            if (parser.startsWith("sax")) securityCheck(context);
            unmarshaller = context.createUnmarshaller();
            marshaller = context.createMarshaller();
            stax = new WstxInputFactory();
        }
        verify(reference);
    }

    /**
     * Reports which SAX factory JAXB obtains and whether it still rejects a DOCTYPE, as the JDK
     * parser does under JAXB's secure-processing defaults. Printed, not asserted, so the
     * measurement still runs and the outcome is visible in the log.
     */
    private void securityCheck(JAXBContext context) {
        System.out.println("# sax factory " + parser + ": " + javax.xml.parsers.SAXParserFactory.newInstance().getClass().getName());
        String[] probes = {
            "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/hostname\">]><r>&x;</r>",
            "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\"><!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]><r>&c;</r>"};
        String[] names = {"external-entity", "entity-expansion"};
        for (int i = 0; i < probes.length; i++) {
            String outcome;
            try {
                context.createUnmarshaller().unmarshal(new ByteArrayInputStream(probes[i].getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                outcome = "ACCEPTED";
            } catch (Throwable failure) {
                Throwable root = failure;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                outcome = "rejected (" + root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage()).replace('\n', ' ') + ")";
            }
            System.out.println("# security " + parser + " " + names[i] + ": " + outcome);
        }
    }

    private void verify(JAXBContext reference) throws Exception {
        String expected = Models.describe(source);
        Object result = run();
        String actual;
        if ("unmarshal".equals(op)) {
            actual = Models.describe(result);
        } else {
            actual = Models.describe(reference.createUnmarshaller().unmarshal(new ByteArrayInputStream((byte[]) result)));
        }
        if (!expected.equals(actual))
            throw new IllegalStateException(impl + " " + op + " " + fixture + " produced a different graph:\n"
                    + expected + "\n" + actual);
    }

    private Object run() throws Exception {
        if ("unmarshal".equals(op)) {
            if (jackson != null) return jackson.readValue(xml, type);
            if ("stax".equals(parser)) {
                XMLStreamReader reader = stax.createXMLStreamReader(new ByteArrayInputStream(xml));
                try {
                    return unmarshaller.unmarshal(reader);
                } finally {
                    reader.close();
                }
            }
            return unmarshaller.unmarshal(new ByteArrayInputStream(xml));
        }
        if (jackson != null) return jackson.writeValueAsBytes(source);
        // As in simdxml-java's benchmark: the byte[] result is part of the operation for every impl.
        ByteArrayOutputStream output = new ByteArrayOutputStream(xml.length);
        marshaller.marshal(source, output);
        return output.toByteArray();
    }

    @Benchmark
    public Object bind() throws Exception {
        return run();
    }
}
