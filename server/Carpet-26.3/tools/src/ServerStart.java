import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

public final class ServerStart {
    private static final URI MOJANG = URI.create("https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar");
    private static final String SHA256 = "d052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64";
    private static final long SIZE = 62294556L;
    private static final Path SERVER = Path.of("server.jar");
    private static final Path EULA = Path.of("eula.txt");

    public static void main(String[] args) {
        int status;
        try {
            status = run();
        } catch (Exception ex) {
            System.err.println("启动失败 / Start failed: " + ex.getMessage());
            status = 1;
        }
        System.exit(status);
    }

    private static int run() throws Exception {
        try (FileChannel lockFile = FileChannel.open(Path.of("tools", "server.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockFile.tryLock()) {
            if (lock == null) {
                System.err.println("这个文件夹中的服务端已经启动。");
                return 1;
            }
            if (!acceptEula()) {
                System.out.println("已取消。可以随时重新双击 start.bat。");
                return 0;
            }
            ensureServerJar();
            String exe = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", exe);
            System.out.println("启动 Fabric 26.3。看到 Done 后即可加入，在本窗口输入 stop 保存关服。");
            Process game = new ProcessBuilder(List.of(java.toString(), "@jvm-args.txt",
                    "-jar", "fabric-server-launch.jar", "nogui")).inheritIO().start();
            return game.waitFor();
        }
    }

    private static boolean acceptEula() throws IOException {
        Properties current = new Properties();
        if (Files.exists(EULA)) {
            try (InputStream input = Files.newInputStream(EULA)) {
                current.load(input);
            }
        }
        if ("true".equalsIgnoreCase(current.getProperty("eula", "").trim())) return true;
        System.out.println("请先阅读 Minecraft 最终用户许可协议 (EULA)");
        System.out.println("https://www.minecraft.net/eula");
        System.out.print("你是否同意这份协议？同意输入 Y 并按回车，其它输入退出 [y/N] ");
        System.out.flush();
        Console console = System.console();
        String answer = console != null ? console.readLine()
                : new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (answer == null || !"y".equalsIgnoreCase(answer.trim())) return false;
        Path temp = Files.createTempFile(Path.of("."), "eula-", ".tmp");
        try {
            Files.writeString(temp, "# https://www.minecraft.net/eula\neula=true\n", StandardCharsets.US_ASCII);
            replace(temp, EULA);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    private static void ensureServerJar() throws Exception {
        if (Files.exists(SERVER)) {
            if (!verified(SERVER)) {
                throw new IOException("server.jar 与 26.3 校验值不符。请将该文件移出开服目录，再启动以重新下载。存档位于 world。");
            }
            return;
        }
        Path partial = Path.of("server.jar.part");
        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    System.out.printf("从 Mojang 下载原版 26.3 服务端 (59.4 MiB)，第 %d/3 次…%n", attempt);
                    download(partial);
                    if (!verified(partial)) throw new IOException("下载文件校验失败");
                    replace(partial, SERVER);
                    return;
                } catch (IOException ex) {
                    if (attempt == 3) throw new IOException("下载失败。请检查网络后重试。 " + MOJANG + " (" + ex.getMessage() + ")", ex);
                    System.err.println(ex.getMessage());
                    Thread.sleep(1000L * attempt);
                }
            }
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static void download(Path file) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) MOJANG.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("User-Agent", "Carpet-Server-Pack/26.3");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream stream = connection.getInputStream()) {
                Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean verified(Path file) throws IOException, NoSuchAlgorithmException {
        if (Files.size(file) != SIZE) return false;
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), hash)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return SHA256.equals(HexFormat.of().formatHex(hash.digest()));
    }

    private static void replace(Path source, Path dest) throws IOException {
        try {
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
