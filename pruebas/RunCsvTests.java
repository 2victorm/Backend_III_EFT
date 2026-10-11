import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.URLClassLoader;
/** Ejecuta pruebas de las reglas CSV sin descargar dependencias Maven. */ public class RunCsvTests  {
    public static void main(String[] args) throws Exception  {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        Path out = Files.createTempDirectory("banco-csv-tests");
        String src = root.resolve("banco-batch/src/main/java/com/duoc/bancoxyz/batch").toString();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Se requiere JDK, no solo JRE");
        int result = compiler.run(null, System.out, System.err, "-d", out.toString(), src + "/Fila.java", src + "/DatoProcesado.java", src + "/ValidadorCsv.java", root.resolve("pruebas/CsvChecks.java").toString());
        if (result != 0) throw new IllegalStateException("No compilaron las reglas CSV");
        try (var loader = new URLClassLoader(new java.net.URL[]  {
            out.toUri().toURL()
        }
        ))  {
            loader.loadClass("com.duoc.bancoxyz.batch.CsvChecks") .getMethod("main", String[].class).invoke(null, (Object)new String[]  {
                root.toString()
            }
            );
        }
        finally  {
            try (var paths = Files.walk(out))  {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
