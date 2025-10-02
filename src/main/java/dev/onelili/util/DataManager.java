package dev.onelili.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onelili.Main;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DataManager {
    @Getter
    private final File dataFolder;
    @Getter
    private final boolean isFirstLaunch;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(Main.getMeta().getInt("max-download-thread"));

    private final AtomicLong totalSize = new AtomicLong(0);
    private final AtomicLong downloadedSize = new AtomicLong(0);

    @SneakyThrows
    public DataManager(@NonNull File dataFolder) {
        this.dataFolder = dataFolder;
        dataFolder.mkdirs();
        File infoFile = new File(dataFolder, "launcher.info");
        isFirstLaunch = !infoFile.exists();
        infoFile.createNewFile();
    }

    public @NonNull CompletableFuture<@NonNull Boolean> downloadFile(@NonNull URL url, @NonNull String name) {
        CompletableFuture<Boolean> ret = new CompletableFuture<>();
        threadPool.submit(() -> {
            try {
                HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                int responseCode = httpConn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    long contentLength = httpConn.getContentLengthLong();
                    totalSize.getAndAdd(contentLength);

                    try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                         FileOutputStream fileOutputStream = new FileOutputStream(new File(dataFolder, name))) {

                        byte[] dataBuffer = new byte[1024];
                        int bytesRead;

                        while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                            fileOutputStream.write(dataBuffer, 0, bytesRead);
                            downloadedSize.getAndAdd(bytesRead);
                        }
                        ret.complete(true);
                    }
                } else
                    ret.complete(false);
            } catch(Exception e) {
                e.printStackTrace();
                ret.complete(false);
            }
        });
        return ret;
    }

    public double getDownloadProgress() {
        long total = Main.getMeta().getInt("packets-total-size");

        return (double)(downloadedSize.get()) / (total > 0 ? total : totalSize.get());
    }

    public @NonNull Optional<AuthData> getAuthData() {
        try {
            File file = new File(dataFolder, "auth.json");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(Files.newInputStream(file.toPath()))).getAsJsonObject();
            return Optional.of(
                    AuthData.deserialize(json)
            );
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    public static @Nullable File findDataFolder() throws IOException {
        File file1 = new File("C:/ProgramData/FastLauncher/");
        File file2 = new File(System.getProperty("user.dir"), "FastLauncher/");
        File file3info = new File(file1, "launcher.index");

        if(file1.exists() && file1.list() != null && Arrays.asList(Objects.requireNonNull(file1.list())).contains("launcher.info"))
            return file1;
        else if(file2.exists() && file2.list() != null && Arrays.asList(Objects.requireNonNull(file2.list())).contains("launcher.info"))
            return file2;
        else if(file3info.exists()) {
            File file3 = new File(new String(Files.readAllBytes(file3info.toPath())));
            if(file3.exists() && file3.list() != null && Arrays.asList(Objects.requireNonNull(file3.list())).contains("launcher.info"))
                return file3;
            else
                file3info.delete();
        }
        return null;
    }

    @SneakyThrows
    public static void main(String[] args) {
        System.out.println(findDataFolder());
    }
}
