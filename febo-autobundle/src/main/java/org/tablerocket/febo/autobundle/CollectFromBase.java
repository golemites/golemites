package org.tablerocket.febo.autobundle;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Collects files from a given base.
 */
public class CollectFromBase implements ContentCollector {

    private File base;

    public CollectFromBase(File base) {
        this.base = base;
    }

    public void collect(Map<String, URL> map) throws IOException {
        collectFromBase(map, base);
    }

    private void collectFromBase(Map<String, URL> map, File dir) throws IOException {
        if (dir != null && dir.canRead() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory()) {
                    collectFromBase(map, f);
                }
                else if (!f.isHidden()) {
                    map.put(normalize(base, f), f.toURI().toURL());
                }
            }
        }
    }

    private String normalize(File _base, File f) throws IOException {
        return f.getCanonicalPath().substring(_base.getCanonicalPath().length() + 1)
            .replace(File.separatorChar, '/');
    }

}
