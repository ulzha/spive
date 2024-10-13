package io.ulzha.spive.basicrunner.util;

import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.lib.HandledException;
import io.ulzha.spive.lib.InternalException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jars {
  private static final Logger LOG = LoggerFactory.getLogger(Jars.class);

  // Example:
  // https://repo.maven.apache.org/maven2/io/ab-cd/excellent-app/0.26.0/excellent-app-0.26.0.jar
  static final Pattern MAVEN_CENTRAL_RE =
      Pattern.compile(
          "^https://repo.maven.apache.org/maven2/[^/]+/([a-z0-9_/-]+)/([^/]+)/([^/]+)/([^/]+\\.jar)$");
  static final Pattern VERSION_RE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
  //  static final Pattern LOCAL_BUILD_RE =
  //      Pattern.compile("^file:///.*/git/([a-z0-9_-]+)/([^/]+)/.*/([^/]+\\.jar)$");
  //  static final Pattern LOCAL_BUILD_VERSION_RE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  /**
   * Validates that the URL points to an allowed repository, and has group ID, artifact ID and
   * version represented in the conventional way.
   */
  private static String getValidJarName(final String artifactUrl) {
    if (artifactUrl.startsWith("file:///")) {
      try {
        return Paths.get(new URI(artifactUrl).getPath()).getFileName().toString();
      } catch (URISyntaxException e) {
        throw new InternalException("Invalid artifactUrl: " + artifactUrl, e);
      }
    }

    final Matcher matcher = MAVEN_CENTRAL_RE.matcher(artifactUrl);

    if (!matcher.matches()) {
      throw new InternalException("Unexpected artifactUrl: " + artifactUrl);
    }

    final MatchResult matchResult = matcher.toMatchResult();
    final String artifactId = matchResult.group(2);
    final String artifactVersion = matchResult.group(3);
    final String jarFileName = matchResult.group(4);

    if (VERSION_RE.matcher(artifactVersion).matches()) {
      throw new InternalException("Unexpected version " + artifactVersion + " in " + artifactUrl);
    }

    if (jarFileName.equals(artifactId + '-' + artifactVersion + ".jar")) {
      throw new InternalException("Unexpected file name in " + artifactUrl);
    }

    return jarFileName;
  }

  private static File getJarDir(final String artifactUrl) throws IOException {
    final Matcher matcher = MAVEN_CENTRAL_RE.matcher(artifactUrl);
    final MatchResult matchResult = matcher.toMatchResult();
    final String groupId =
        (artifactUrl.startsWith("file:///")
            ? System.getProperty("user.name")
            : matchResult.group(1).replaceAll("/", "."));

    final File stagingDir = new File(System.getProperty("user.dir"), "jars");
    final File dir = new File(stagingDir, groupId);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("failed to create directory: " + dir);
    }
    return dir;
  }

  /**
   * Downloads a jar, if the given version has not already been downloaded, into directory structure
   * under the current directory.
   *
   * @return the jar file
   */
  public static File getJar(final String artifactUrl) throws IOException {
    final String jarName = getValidJarName(artifactUrl);
    final File jarFile = new File(getJarDir(artifactUrl), jarName);

    if (!jarFile.exists()) {
      final URL jarUrl = new URL(artifactUrl);

      try (InputStream inputStream = jarUrl.openStream()) {
        LOG.info("Downloading " + jarUrl + " to " + jarFile);
        Files.copy(inputStream, jarFile.toPath()); // FIXME stage and atomically move into place
      }
    }

    return jarFile;
  }

  public static void runJar(
      final File jarFile,
      final String className,
      final String methodName,
      final Umbilical umbilical,
      final String[] args) {
    //    JarFile jarFile = new JarFile(file);
    //    Enumeration<JarEntry> jarComponents = jarFile.entries();
    //
    //    while (jarComponents.hasMoreElements())
    //    {
    //      JarEntry entry = jarComponents.nextElement();
    //      if(entry.getName().endsWith(".class"))
    //      {
    //        className = entry.getName();
    //      }
    //    }
    final URL[] urls;
    try {
      urls = new URL[] {jarFile.toURI().toURL()};
    } catch (MalformedURLException e) {
      // ought to reach the uncaught exception handler. TODO test
      throw new InternalException(
          "Error converting "
              + jarFile
              + "to URL - should never happen unless toURI does weird things",
          e);
    }
    URLClassLoader loader = new URLClassLoader(urls);
    runInClassLoader(loader, className, methodName, umbilical, args);
  }

  private static void runInClassLoader(
      final URLClassLoader loader,
      final String className,
      final String methodName,
      final Umbilical umbilical,
      final String[] args) {
    try {
      Class.forName(className, true, loader)
          .getMethod(methodName, Umbilical.class, String[].class)
          .invoke(null, umbilical, args);
    } catch (IllegalAccessException | ClassNotFoundException | NoSuchMethodException e) {
      // ought to reach the uncaught exception handler. TODO test
      throw new InternalException(
          "Error invoking "
              + className
              + "."
              + methodName
              + "() - should never happen if, upon creating a process, the jar was reliably checked",
          e);
    } catch (InvocationTargetException e) {
      if (!(e.getCause() instanceof HandledException)
          || umbilical.addError(null, e.getCause().getCause())) {
        throw new InternalException(
            "Erroneous return from "
                + className
                + "."
                + methodName
                + "() - umbilical should have captured the error if the jar used Spive generated exception handling code, for which the jar should be reliably (?) checked",
            e);
      }
      // otherwise no further handling needed - cause is captured in umbilical
    }
  }
}
