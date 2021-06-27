package info.kgeorgiy.ja.tkachenko.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * {@code Implementor} implements interfaces {@link Impler} and {@link JarImpler}
 * <p>
 * Generates class implementations of given interfaces.
 * </p>
 *
 * @author Tkachenko
 * @version 1.0
 */
public class Implementor implements Impler, JarImpler {

    /**
     * {@link String} for classes' suffix
     */
    private final static String NAME_SUFFIX = "Impl.java";

    /**
     * Console interface for {@code Implementor}.
     *
     * @param args correct usage:
     *             <p>
     *             {@code <InterfaceName> <DirectoryName>} creates implementation of given token and places it
     *             in given root directory.
     *             </p>
     */
    public static void main(String[] args) {
        if (checkArgs(args)) {
            try {
                if (args.length == 2) {
                    new Implementor().implement(Class.forName(args[0]), Path.of(args[1]));
                } else if (args.length == 3) {
                    // RESOLVED :NOTE: "-jar"
                    if (args[0].equals("jar")) {
                        new Implementor().implementJar(Class.forName(args[1]), Path.of(args[2]));
                    } else {
                        System.err.println("Invalid arguments, should be jar <InterfaceName> <DirectoryName>");
                    }

                }
            } catch (ClassNotFoundException e) {
                System.err.println("Class not found");
            } catch (ImplerException e) {
                System.err.println(e.getMessage());
            }
        }

    }

    /**
     * Checks if given arguments are valid.
     *
     * @param args given args.
     * @return whether args are valid or not.
     */
    private static boolean checkArgs(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[1] == null) {
            System.err.println("Invalid arguments, should be <InterfaceName> <DirectoryName> or " +
                    "jar <InterfaceName> <DirectoryName>");
            return false;
        }
        return true;
    }


    /**
     * Creates implementation for {@link Class token} using private class {@link Generator}.
     *
     * @param token type token to create implementation for.
     * @param root  root directory.
     * @throws ImplerException if there were errors while writing to file or implementing given interface.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        int modifiers = token.getModifiers();
        if (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers)) {
            throw new ImplerException("Error: the interface is private or final");
        }
        Path path = getPath(root, token);
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(new Generator(token).generateCode());
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }
    }


    /**
     * Creates jar file located at given {@link Path} {@code jarFile} containing
     * implementation of given {@link Class token}.
     *
     * @param token   type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if could not create jar
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        try {
            Path tempDir = Files.createTempDirectory(jarFile.toAbsolutePath().getParent(), "tmp");
            try {
                implement(token, tempDir);
                compile(tempDir, token);
                buildJar(tempDir, jarFile, token);
            } finally {
                deleteDirs(tempDir.toFile());
            }
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }

    }

    /**
     * Compiles implementation of {@link Class token} with {@link JavaCompiler}
     *
     * @param root  {@link Path} to temporary directory for <var>.jar</var> file
     * @param token type token to create implementation for.
     * @throws ImplerException if fails to compile.
     */
    private static void compile(Path root, Class<?> token) throws ImplerException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Could not find java compiler, include tools.jar to classpath");
        }
        String[] args = {"-cp",
                root + File.pathSeparator + getClassPath(token),
                getFullPath(root, token).toString()};
        int exitCode = compiler.run(null, null, null, args);
        if (exitCode != 0) {
            throw new ImplerException("Compiler exit code");
        }
    }

    /**
     * Builds <var>.jar</var> file for compiled {@link Class token} implementation class.
     *
     * @param temp    {@link Path} to temporary directory for <var>.jar</var> file.
     * @param jarPath default jar {@link Path}.
     * @param token   type token to create implementation for.
     * @throws ImplerException if fails to build <var>.jar</var>.
     */
    private static void buildJar(Path temp, Path jarPath, Class<?> token) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            String implementationName = getResolvedPackages(token, "/") + "/" + token.getSimpleName() + "Impl.class";
            out.putNextEntry(new ZipEntry(implementationName));
            Files.copy(temp.resolve(implementationName), out);
        } catch (IOException e) {
            throw new ImplerException("Error when working with jar file");
        }

    }

    /**
     * Removes needless temporary directories and subdirectories.
     *
     * @param file temporary directory.
     */
    private static void deleteDirs(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File value : files) {
                deleteDirs(value);
            }
        }
        file.delete();
    }

    /**
     * Returns full {@link Path} of {@link Class token} implementation with the suffix.
     *
     * @param root  root directory.
     * @param token type token to create implementation for.
     * @return full {@link Path} of {@link Class token} implementation.
     */
    private static Path getFullPath(Path root, Class<?> token) {
        return root.resolve(Path.of(getResolvedPackages(token, File.separator),
                token.getSimpleName() + NAME_SUFFIX));
    }

    /**
     * Returns class {@link Path} of given {@link Class token}.
     *
     * @param token type token to create implementation for.
     * @return {@link String} with class path of token.
     */
    private static String getClassPath(Class<?> token) {
        try {
            return Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Creates new {@link Path} with given {@link Path root} and packages
     * of {@link Class token}.
     *
     * @param root  root directory.
     * @param token type token to create implementation for.
     * @return {@link Path} to implemented class.
     * @throws ImplerException if failed to create directories or path.
     */
    private Path getPath(Path root, Class<?> token) throws ImplerException {
        Path path;
        try {
            path = getFullPath(root, token);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }
        return path;
    }

    /**
     * Returns {@link String} with packages separated by given separator.
     *
     * @param token     type token to create implementation for.
     * @param separator instead of ".".
     * @return {@link String} with packages.
     */
    private static String getResolvedPackages(Class<?> token, String separator) {
        return token.getPackageName().replace(".", separator);
    }

/**
 * Private class for generating implementations of given {@code token}.
 */
private static class Generator {
    /**
     * {@link Class} private field of given token.
     */
    private final Class<?> token;
    /**
     * {@link String} field containing the {@link #token} implementation code.
     */
    private String classCode;
    /**
     * {@link String} field for System line separator.
     */
    private final String LINE_SEPARATOR = System.lineSeparator();
    /**
     * {@link String} field for tabulation.
     */
    private final String TAB = "    ";

    /**
     * Constructor of {@link Generator}, defines new {@link #classCode} and store new {@link #token}.
     *
     * @param token type token to create implementation for.
     */
    Generator(Class<?> token) {
        this.token = token;
        classCode = "";
    }

    /**
     * Generates {@link String} code of the implemented class.
     *
     * @return {@link String} text of implemented class.
     */
    String generateCode() {
        addPackageAndName();
        addMethods();
        return toUnicode(classCode);
    }

    /**
     * Converts given {@link String} to Unicode.
     *
     * @param s {@link String}.
     * @return converted {@link String}.
     */
    private String toUnicode(String s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 128) {
                stringBuilder.append(s.charAt(i));
            } else {
                stringBuilder.append("\\u").append(String.format("%04x", (int) s.charAt(i)));
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Joins given array of {@link String} with given separator.
     *
     * @param separator separator for joining strings.
     * @param strings   array of strings to join.
     * @return {@link String} - concatenated strings.
     */
    private String concatenate(String separator, String... strings) {
        return String.join(separator, strings);
    }

    /**
     * Adding {@code package} and name of given class.
     */
    private void addPackageAndName() {
        if (!token.getPackageName().isEmpty()) {
            classCode += "package " + token.getPackageName() + ";" + LINE_SEPARATOR;
        }
        classCode += LINE_SEPARATOR + concatenate(" ", "public class",
                token.getSimpleName() + "Impl", "implements", token.getCanonicalName(), "{")
                + LINE_SEPARATOR;
    }

    /**
     * Adding methods to {@link #classCode}.
     */
    private void addMethods() {
        Method[] methods = token.getMethods();
        classCode += Arrays.stream(methods).map(this::methodCode).collect(Collectors.joining()) + "}";
    }

    /**
     * Concatenates name and body of given {@link Method}.
     *
     * @param method {@link Method} that needs to be added.
     * @return {@link String} text of given method.
     */
    private String methodCode(Method method) {
        return concatenate(LINE_SEPARATOR + TAB, TAB, methodName(method), methodBody(method), "}",
                LINE_SEPARATOR);
    }

    /**
     * Creates the name of given {@link Method}.
     *
     * @param method {@link Method} that needs to be added.
     * @return {@link String} name of given method.
     */
    private String methodName(Method method) {
        return concatenate(" ", "public", method.getReturnType().getCanonicalName(),
                method.getName() + methodParameters(method), methodExceptions(method), "{");
    }

    /**
     * Creates {@link String} containing list of {@link Parameter} of given method.
     *
     * @param method {@link Method}.
     * @return {@link String} containing list of {@link Parameter} and their names.
     */
    private String methodParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        return "(" + concatenate(", ",
                Arrays.stream(parameters)
                        .map(parameter -> parameter.getType().getCanonicalName() + " " + parameter.getName())
                        .toArray(String[]::new))
                + ")";
    }

    /**
     * Creates {@link String} containing list of {@link Class} of exceptions of given method.
     *
     * @param method {@link Method}.
     * @return {@link String} containing list of the method's throwable exceptions.
     */
    private String methodExceptions(Method method) {
        String exceptionsText = "";
        Class<?>[] exceptions = method.getExceptionTypes();
        int n = exceptions.length;
        if (n > 0) {
            exceptionsText += "throws " + (Arrays.stream(exceptions)
                    .map(Class::getCanonicalName)
                    .collect(Collectors.joining(", ")));
        }
        return exceptionsText;
    }

    /**
     * Create {@link String} of given method's body.
     *
     * @param method {@link Method}.
     * @return {@link String} containing given method's body.
     */
    private String methodBody(Method method) {
        String bodyText = "";
        Class<?> returnType = method.getReturnType();
        bodyText += TAB + "return";
        if (returnType.equals(boolean.class)) {
            // RESOLVED :NOTE: false
            bodyText += " false";
        } else if (returnType.equals(void.class)) {
            bodyText += "";
        } else if (returnType.isPrimitive()) {
            bodyText += " 0";
        } else {
            bodyText += " null";
        }
        bodyText += ";";
        return bodyText;
    }
}
}
