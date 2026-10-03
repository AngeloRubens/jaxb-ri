package bench;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBContextFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Loads one jaxb-ri build ("variant") in an isolated class loader.
 *
 * <p>The JMH host class path holds only the JAXB API, its shared dependencies (activation,
 * istack, txw2), Jackson and the model classes. {@code jaxb-core} and {@code jaxb-runtime} of
 * the variant are read from {@code variants/<name>/*.jar}, so baseline master and every branch
 * run from the same benchmark binary, in the same JMH invocation, each fork loading exactly one
 * variant. The value {@code jackson} selects Jackson XML instead of JAXB.
 */
public final class Impl {
    private Impl() {}

    public static boolean isJackson(String impl) {
        return "jackson".equals(impl);
    }

    public static JAXBContext context(String impl, Class<?>... classes) throws Exception {
        File dir = new File(System.getProperty("bench.variants", "variants"), impl);
        File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null || jars.length == 0)
            throw new IllegalStateException("No jars for variant '" + impl + "' in " + dir);
        Arrays.sort(jars);
        List<URL> urls = new ArrayList<>();
        StringBuilder label = new StringBuilder("# variant ").append(impl).append(':');
        for (File jar : jars) {
            urls.add(jar.toURI().toURL());
            label.append(' ').append(jar.getName()).append('@').append(sha(jar));
        }
        System.out.println(label);
        ClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), Impl.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        JAXBContextFactory factory = (JAXBContextFactory) loader
                .loadClass("org.glassfish.jaxb.runtime.v2.JAXBContextFactory")
                .getDeclaredConstructor().newInstance();
        JAXBContext context = factory.createContext(classes, Collections.emptyMap());
        if (context.getClass().getClassLoader() != loader)
            throw new IllegalStateException("JAXBContext not loaded from the variant: " + context.getClass());
        return context;
    }

    private static String sha(File jar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(jar.toPath())) {
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) md.update(b, 0, n);
        } catch (IOException e) {
            throw e;
        }
        return HexFormat.of().formatHex(md.digest()).substring(0, 12);
    }
}
