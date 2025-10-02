package dev.onelili;

import cn.jason31416.adaptInstaller.MinecraftInstaller;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onelili.swing.*;
import dev.onelili.util.*;
import fr.litarvan.openauth.model.response.AuthResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.to2mbn.jmccc.auth.AuthInfo;
import org.to2mbn.jmccc.launch.Launcher;
import org.to2mbn.jmccc.launch.LauncherBuilder;
import org.to2mbn.jmccc.option.JavaEnvironment;
import org.to2mbn.jmccc.option.LaunchOption;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.util.UUIDUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Main {
    @Getter
    private static MapTree meta;
    @Getter
    private static DataManager data;

    @SneakyThrows
    public static void main(String[] args) {
        FlatMacLightLaf.setup();

        StringBuilder sb = new StringBuilder();
        try(InputStream in = Main.class.getClassLoader().getResourceAsStream("meta.json")){
            byte[] buffer = new byte[1024];
            int len;
            while((len = in.read(buffer)) > 0)
                sb.append(new String(buffer, 0, len));
        }
        meta = MapTree.fromJson(sb.toString());

        File dataFolder = DataManager.findDataFolder();
        if(dataFolder == null) {
            CompletableFuture<File> future = new CompletableFuture<>();
            new DirectoryChooser(future::complete);
            File dataDir = future.get();
            data = new DataManager(dataDir);
//            firstInitialize();
        } else
            data = new DataManager(dataFolder);

        new MinecraftInstaller(meta.getSection("adapt"))
                .install(data.getDataFolder().getAbsolutePath());

        MinecraftDirectory dir = new MinecraftDirectory(data.getDataFolder()+"/"+meta.getString("adapt.name"));
        Launcher launcher = LauncherBuilder.buildDefault();

        AuthData authData = data.getAuthData().orElse(null);
        if(authData == null) {
            CompletableFuture<AuthData> future = new CompletableFuture<>();
            new LoginWindow(future::complete);
            authData = future.get();

            File file = new File(data.getDataFolder(), "auth.json");
            try(FileWriter writer = new FileWriter(file)) {
                writer.write(new Gson().toJson(AuthData.serialize(authData)));
            }
        }

        LaunchPrompt prompt = new LaunchPrompt();

        final AuthData finalAuthData = authData;
        final AuthResponse finalAuthResponse = authData.getResponse() == null ? AccountUtils.login(finalAuthData) : authData.getResponse();
        final AuthInfo finalAuthInfo = new AuthInfo(
                finalAuthData.getUsername(),
                finalAuthResponse.getAccessToken(),
                UUIDUtils.toUUID(Objects.requireNonNull(finalAuthResponse).getSelectedProfile().getId()),
                new HashMap<>(),
                "msa",
                "${auth_xuid}"
        );

        LaunchOption option = new LaunchOption(
                meta.getString("version-name"),
                () -> finalAuthInfo,
                dir
        );

        if(meta.get("java-relative-path") == null || meta.getString("java-relative-path").isEmpty() || meta.getString("java-relative-path").equals("%this%"))
            option.setJavaEnvironment(JavaEnvironment.current());
        else
            option.setJavaEnvironment(new JavaEnvironment(new File(data.getDataFolder(), meta.getString("java-relative-path"))));
        option.extraMinecraftArguments().add("--quickPlayMultiplayer");
        option.extraMinecraftArguments().add(meta.getString("minecraft-server"));

        JsonObject authMeta = new JsonObject();
        authMeta.addProperty("timestamp", System.currentTimeMillis());
        authMeta.addProperty("profileId", finalAuthResponse.getSelectedProfile().getId());
        authMeta.addProperty("profileName", finalAuthResponse.getSelectedProfile().getName());

        option.extraJvmArguments().add(String.format("-javaagent:%s=%s", new File(data.getDataFolder(), "authlib-injector.jar").getAbsolutePath(), meta.get("uni-auth-url") + "yggdrasil/"));
        option.extraJvmArguments().add("-Dauthlibinjector.side=client");

        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch(InterruptedException ignored) {}
            prompt.close();
        }).start();

        launcher.launch(option).waitFor();
    }

//    @SneakyThrows
//    private static void firstInitialize() {
//        Set<CompletableFuture<@NonNull Boolean>> allFutures = new HashSet<>();
//        List<@NonNull String> files = new ArrayList<>();
//
//        for(JsonElement element : meta.get("packets")) {
//            URL url = new URL(element.getAsString());
//            allFutures.add(data.downloadFile(url, element.getAsString().substring(element.getAsString().lastIndexOf('/') + 1)));
//            files.add(element.getAsString().substring(element.getAsString().lastIndexOf('/') + 1));
//        }
//
//        ProgressBar bar = new ProgressBar(meta.get("packets-total-size").getAsLong());
//        while(!allFutures.stream().allMatch(CompletableFuture::isDone))
//            bar.update((long) (data.getDownloadProgress() * meta.get("packets-total-size").getAsLong()));
//
//        bar.setStatus("正在解压缩...");
//
//        try(ZipFile zipFile = new ZipFile(new File(data.getDataFolder(), files.get(0)))) {
//            zipFile.extractAll(data.getDataFolder().getAbsolutePath());
//            for(String file : files)
//                new File(data.getDataFolder(), file).delete();
//        }
//
//        bar.setStatus("正在加载authlib-injector...");
//
//        File injector = new File(data.getDataFolder(), "authlib-injector.jar");
//        try(InputStream in = Main.class.getClassLoader().getResourceAsStream("authlib-injector.jar")) {
//            Files.copy(Objects.requireNonNull(in), injector.toPath(), StandardCopyOption.REPLACE_EXISTING);
//        }
//
//        bar.close();
//    }
}
