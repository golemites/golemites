package org.golemites.plugin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import okio.BufferedSink;
import org.golemites.api.Dependency;
import org.golemites.api.TargetPlatformSpec;
import org.golemites.launcher.Launcher;
import org.golemites.repository.ClasspathRepositoryStore;
import org.golemites.repository.EmbeddedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

import static okio.Okio.buffer;
import static okio.Okio.sink;
import static okio.Okio.source;
import static org.golemites.repository.ClasspathRepositoryStore.BLOB_FILENAME;

/**
 * Combines a given platform with application artifacts and creates a ready to deploy/run situation.
 */
public class ImageBuilder {
    private final static Logger LOG = LoggerFactory.getLogger(ImageBuilder.class);

    private final Path targetBase;

    public ImageBuilder( Path targetBase) {
        this.targetBase = targetBase;
    }

    /**
     * Collects are necessary artifacts and forms so that an actual image can be created or started.
     *
     * @param inputSpec platform descriptor.
     * @return a ready to be used flat description of a concrete Application
     */
    public TargetPlatformSpec prepare(TargetPlatformSpec inputSpec) {
        try {
            List<Path> platformArtifacts = install(Arrays.asList(inputSpec.getDependencies()),TargetPlatformSpec.platformPath(targetBase));
            Path specArtifact = Files.createDirectories(TargetPlatformSpec.configuration(targetBase)).resolve(BLOB_FILENAME);
            writeBlob(inputSpec,specArtifact);
            return inputSpec;
        }catch (Exception e) {
            throw new RuntimeException("Problem!",e);
        }
    }

    private List<Path> install(List<Dependency> external,Path path) throws IOException {
        List<Path> appPaths = new ArrayList<>();
        Files.createDirectories(path);
        for (Dependency d : external) {
            Path localTarget = path.resolve( d.getIdentity() + ".jar");
            try (InputStream in = d.getLocation().toURL().openStream(); BufferedSink sink = buffer(sink(localTarget))) {
                sink.writeAll(source(in));
            }
            // constructed string where we expect to find the content again later.
            d.setLocation(URI.create("file:///"+path.getFileName().toString()+"/" + d.getIdentity() + ".jar"));
            appPaths.add(localTarget);
        }
        return appPaths;
    }

    public void assembleJar(Path output, URI launcher, TargetPlatformSpec inputSpec) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Launcher.class.getName());
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            Set<String> ignore = new HashSet<>(Arrays.asList("META-INF/DEPENDENCIES", "META-INF/MANIFEST.MF", "META-INF/LICENSE", "META-INF/NOTICE"));
            writeLauncherDeps(launcher, ignore, jos);
            Path path = TargetPlatformSpec.platformPath(Paths.get("/"));
            for (Dependency d : inputSpec.getDependencies()) {
                Path localTarget = path.resolve(d.getIdentity() + ".jar");
                jos.putNextEntry(new JarEntry(localTarget.toString()));
                URI location = EmbeddedStore.prefixWithBase(this.targetBase,d.getLocation());
                try (InputStream in = location.toURL().openStream()) {
                    writeAll(jos,in);
                }
            }
            writeBlob(inputSpec, jos);
        }
    }

    private void writeBlob(TargetPlatformSpec targetPlatformSpec, JarOutputStream jos) throws IOException {
        jos.putNextEntry(new JarEntry(BLOB_FILENAME));
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(jos, targetPlatformSpec);
    }

    private void writeBlob(TargetPlatformSpec targetPlatformSpec, Path here) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (OutputStream fos = Files.newOutputStream(here)) {
            mapper.writeValue(fos, targetPlatformSpec);
        }
    }

    public TargetPlatformSpec findSpec(List<URI> c) throws IOException {
        for (URI artifact : c) {
            // here we first need to discover and resolve the existing blob file:
            LOG.info("++ Project artifact: " + artifact.toASCIIString());
            // find blobs:
            File f = new File(artifact);
            if (f.isFile()) {
                try (JarInputStream jis = new JarInputStream(new FileInputStream(f))) {
                    JarEntry entry = null;
                    while ((entry = jis.getNextJarEntry()) != null) {
                        LOG.debug("Checking " + entry.getName() + " ---- ");
                        if (BLOB_FILENAME.equals(entry.getName())) {
                            return new ClasspathRepositoryStore(buffer(source(jis)).readByteArray()).platform();
                        }
                        LOG.debug("Writing entry: " + entry.getName());
                    }
                }
            }
        }
        throw new RuntimeException("Spec not found!");
    }

    private void writeLauncherDeps(URI launcher, Set<String> ignore, JarOutputStream jos) throws IOException {
        try (JarInputStream jis = new JarInputStream(launcher.toURL().openStream())) {
            JarEntry entry = null;
            while ((entry = jis.getNextJarEntry()) != null) {
                LOG.debug("Writing entry: " + entry.getName());
                if (!ignore.contains(entry.getName())) {
                    try {
                        jos.putNextEntry(entry);
                        writeAll(jos, jis);
                    } catch (ZipException ze) {
                        LOG.debug("Duplicate entry " + ze.getMessage());
                    }

                }
            }
        }
    }

    private void writeAll(OutputStream jos, InputStream jis) throws IOException {
        byte[] buffer = new byte[1024];
        while (true) {
            int count = jis.read(buffer);
            if (count == -1)
                break;
            jos.write(buffer, 0, count);
        }
    }
}
