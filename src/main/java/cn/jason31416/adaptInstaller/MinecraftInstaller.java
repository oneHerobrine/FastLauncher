package cn.jason31416.adaptInstaller;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import dev.onelili.util.MapTree;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloader;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloaderBuilder;
import org.to2mbn.jmccc.mcdownloader.RemoteVersionList;
import org.to2mbn.jmccc.mcdownloader.download.combine.CombinedDownloadTask;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.CallbackAdapter;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.DownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.tasks.DownloadTask;
import org.to2mbn.jmccc.mcdownloader.provider.DownloadProviderChain;
import org.to2mbn.jmccc.mcdownloader.provider.MinecraftDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.MojangDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.fabric.FabricDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.forge.ForgeDownloadProvider;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.util.FileUtils;
import org.to2mbn.jmccc.version.Asset;
import org.to2mbn.jmccc.version.Library;
import org.to2mbn.jmccc.version.Version;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MinecraftInstaller {
    public static interface CompleteChecker{
        boolean isComplete();
    }

    private ExecutorService threadPool;
    private final JFrame frame;
    private final MapTree versionManifest;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private MinecraftDownloader downloader;
    private MinecraftDirectory dir;
    private String directory;
    private final Set<CompleteChecker> runningTasks = new HashSet<>();
    private final AtomicInteger downloadedFiles = new AtomicInteger(0), totalFiles = new AtomicInteger(0);

    public MinecraftInstaller(MapTree versionManifest) {
        this.versionManifest = versionManifest;
        if(versionManifest.getString("name").contains("/")||versionManifest.getString("name").contains("..")){
            throw new SecurityException("Found invalid character in version name: "+versionManifest.getString("name")+"!");
        }
        frame = new JFrame("安装 "+versionManifest.get("name")+" 中...");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(50, 150, 255));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel("下载文件中...", JLabel.CENTER);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(Box.createVerticalGlue());
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalGlue());
        mainPanel.add(progressBar);
        mainPanel.add(Box.createVerticalGlue());

        frame.add(mainPanel);
    }

    private CompleteChecker downloadFile(@NonNull URL url, @NonNull File toFile) {
        Future<?> future = threadPool.submit(() -> {
            try {
                HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                int responseCode = httpConn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    long contentLength = httpConn.getContentLengthLong();

                    try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                         FileOutputStream fileOutputStream = new FileOutputStream(toFile)) {

                        byte[] dataBuffer = new byte[1024];
                        int bytesRead;

                        while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                            fileOutputStream.write(dataBuffer, 0, bytesRead);
                        }
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
        return future::isDone;
    }
    @SneakyThrows
    private String fetchRemoteContent(String url){
        URL remoteUrl = new URL(url);
        HttpURLConnection httpConn = (HttpURLConnection) remoteUrl.openConnection();
        int responseCode = httpConn.getResponseCode();
        StringBuilder content = new StringBuilder();
        try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream())) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                content.append(new String(dataBuffer, 0, bytesRead));
            }
        }
        return content.toString();
    }
    private CompleteChecker downloadMinecraftVersion(String version){
        System.out.println("Downloading "+version+"...");
        Future<Version> future = downloader.downloadIncrementally(dir, version, new CallbackAdapter<Version>() {
            @Override
            public void failed(Throwable e) {
                // when the task fails
                e.printStackTrace();
            }

            @Override
            public void done(Version result) {
                // when the task finishes
                System.out.println("Done downloading version "+version);
            }

            @Override
            public void cancelled() {
                // when the task cancels
                System.out.println("Cancelled downloading version "+version);
            }

            @Override
            public <R> DownloadCallback<R> taskStart(DownloadTask<R> task) {
                // when a new sub download task starts
                // return a DownloadCallback to listen the status of the task
                System.out.println("Starting download: "+task.getURI());
                totalFiles.incrementAndGet();

                return new CallbackAdapter<R>() {

                    @Override
                    public void done(R result) {
                        // when the sub download task finishes
                        downloadedFiles.incrementAndGet();
                    }

                    @Override
                    public void failed(Throwable e) {
                        // when the sub download task fails
                        e.printStackTrace();
                        downloadedFiles.incrementAndGet();
                    }

                    @Override
                    public void cancelled() {
                        // when the sub download task cancels
                        downloadedFiles.incrementAndGet();
                    }

                    @Override
                    public void updateProgress(long done, long total) {
                        // when the progress of the sub download task has updated
                    }

                    @Override
                    public void retry(Throwable e, int current, int max) {
                        // when the sub download task fails, and the downloader decides to retry the task
                        // in this case, failed() won't be called
                        System.out.println("Retrying "+task.getURI()+" "+current+"/"+max);
                    }
                };
            }
        });
        return future::isDone;
    }
    private CompleteChecker downloadModificationFiles(MapTree modifications){
        Set<CompleteChecker> tasks = new HashSet<>();
        for(String path: modifications.getKeys()){
            File file = new File(directory+"/"+versionManifest.getString("name")+"/"+path);
            if(file.exists()){
                System.out.println("Skipping "+path+" because it already exists");
                continue;
            }
            if(path.startsWith("/")||path.startsWith("..")){
                System.out.println("\033[31mError: Found potentially dangerous path: "+path+"!\033[0m");
                throw new SecurityException("Found potentially dangerous path: "+path+"!");
            }
            System.out.println("Installing "+path);
            if(modifications.getString(path).startsWith("pack://")){
                throw new UnsupportedOperationException("Cannot load pack:// resources in non-modpack context!");
            }
            try {
                tasks.add(downloadFile(new URL(modifications.getString(path)), file));
            } catch (MalformedURLException e) {
                System.out.println("\033[31mError: Malformed URL: "+modifications.getString(path)+"!\033[0m");
            }
        }
        return () -> tasks.stream().allMatch(CompleteChecker::isComplete);
    }
    private void deleteFileDirectory(File file){
        if(file.isDirectory()){
            for(File f: file.listFiles()){
                deleteFileDirectory(f);
            }
        }else{
            file.delete();
        }
    }
    private void runInstallationScript(List<Map<String, Object>> installation){
        nextStep:
        for(Map<String, Object> i: installation) {
            MapTree step = new MapTree(i);
            String type = step.getString("type");
            if(step.contains("condition")){
                for(String condition: step.getSection("condition").getKeys()){
                    String value = step.getString("condition."+condition);
                    if(condition.equals("os")){
                        if(!System.getProperty("os.name").toLowerCase().matches(value)){
                            continue nextStep;
                        }
                    }else if(condition.equals("fileExist")){
                        if(!new File(directory+"/"+versionManifest.getString("name")+"/"+value).exists()){
                            continue nextStep;
                        }
                    }else if(condition.equals("fileNotExist")){
                        if(new File(directory+"/"+versionManifest.getString("name")+"/"+value).exists()){
                            continue nextStep;
                        }
                    }
                }
            }
            System.out.println("Installing step "+type);
            switch (type) {
                case "installBase": {
                    runningTasks.add(downloadMinecraftVersion(step.getString("version")));
                    break;
                }
                case "installMod": {
                    runningTasks.add(downloadModificationFiles(step.getSection("modifications")));
                    break;
                }
                case "delete": {
                    runningTasks.add(threadPool.submit(() -> {
                        String file = step.getString("file");
                        if(file.startsWith("/")||file.contains("..")){
                            System.out.println("\033[31mError: Found potentially dangerous path: "+file+"!\033[0m");
                            throw new SecurityException("Found potentially dangerous path: "+file+"!");
                        }
                        File f = new File(directory+"/"+versionManifest.getString("name")+"/"+file);
                        if(f.exists()){
                            try{
                                deleteFileDirectory(f);
                            }catch (Exception e){
                                e.printStackTrace();
                                System.out.println("\033[31mError: Failed to delete file: "+file+"!\033[0m");
                            }
                        }
                    })::isDone);
                    break;
                }
                case "unzip": {
                    runningTasks.add(threadPool.submit(() -> {
                        String file = step.getString("file");
                        String to = step.getString("to");
                        if (file.startsWith("/") || file.contains("..")) {
                            System.out.println("\033[31mError: Found potentially dangerous path: " + file + "!\033[0m");
                            throw new SecurityException("Found potentially dangerous path: " + file + "!");
                        }
                        try (ZipFile zipFile = new ZipFile(new File(directory + "/" + versionManifest.getString("name") + "/" + file))) {
                            zipFile.extractAll(directory + "/" + versionManifest.getString("name") + "/" + to);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })::isDone);
                    break;
                }
                case "edit": {
                    String file = step.getString("file");
                    byte[] content = Base64.getDecoder().decode(step.getString("content"));
                    File f = new File(directory+"/"+versionManifest.getString("name")+"/"+file);
                    try (FileOutputStream out = new FileOutputStream(f)){
                        out.write(content);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                }
                case "await": {
                    while (!runningTasks.stream().allMatch(CompleteChecker::isComplete)) {
                        try {
                            Thread.sleep(20);
                            if(totalFiles.get()>0) {
                                progressBar.setValue(downloadedFiles.get() * 100 / totalFiles.get());
                                progressBar.setString(String.format("%d/%d", downloadedFiles.get(), totalFiles.get()));
                            }
                            frame.repaint();
                        }catch (InterruptedException e){
                            break;
                        }
                    }
                    break;
                }
                default: {
                    System.out.println("\033[31mError: Unknown step type: "+type+"!\033[0m");
                    break;
                }
            }
        }
    }
    @SneakyThrows
    public void install(String baseDirectory){
        directory = baseDirectory;

        frame.setVisible(true);
        threadPool = Executors.newFixedThreadPool(versionManifest.getInt("threads", 4));
        System.out.println("Downloading version "+versionManifest.getString("name"));
        dir = new MinecraftDirectory(directory+"/"+versionManifest.getString("name"));
        ForgeDownloadProvider forgeProvider = new ForgeDownloadProvider();
        FabricDownloadProvider fabricProvider = new FabricDownloadProvider();
        downloader = MinecraftDownloaderBuilder
                .create()
                .providerChain(DownloadProviderChain.create().addProvider(new MojangDownloadProvider()).addProvider(forgeProvider).addProvider(fabricProvider))
                .build();
//        downloader = MinecraftDownloaderBuilder.buildDefault();

        if(new File(directory+"/"+versionManifest.getString("name")).exists()){
            if(new File(directory+"/"+versionManifest.getString("name")+"/adaptmeta.json").exists()){
                StringBuilder content = new StringBuilder();
                try(FileInputStream in = new FileInputStream(directory+"/"+versionManifest.getString("name")+"/adaptmeta.json")){
                    byte[] dataBuffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                        content.append(new String(dataBuffer, 0, bytesRead));
                    }
                }
                try{
                    MapTree meta = MapTree.fromJson(content.toString());
                    if(versionManifest.contains("packVersion")){
                        if(!versionManifest.getString("packVersion").equals(meta.getString("packVersion", "unknown"))){
                            System.out.println("Version does not match, attempting to update...");
                            deleteFileDirectory(new File(directory+"/"+versionManifest.getString("name")));
                        }else{
                            System.out.println("Version already installed, skipping.");
                            frame.dispose();
                            downloader.shutdown();
                            threadPool.shutdown();
                            return;
                        }
                    }else{
                        System.out.println("Version not specified while found installed version, skipping.");
                        frame.dispose();
                        downloader.shutdown();
                        threadPool.shutdown();
                        return;
                    }
                }catch (Exception e){
                    System.out.println("\033[31mError: Failed to parse adaptmeta.json!\033[0m");
                }
            }
        }

        if(versionManifest.contains("installation"))
            runInstallationScript((List<Map<String, Object>>) versionManifest.get("installation"));

        while (!runningTasks.stream().allMatch(CompleteChecker::isComplete)) {
            try {
                Thread.sleep(20);
                if(totalFiles.get()>0) {
                    progressBar.setValue(downloadedFiles.get() * 100 / totalFiles.get());
                    progressBar.setString(String.format("%d/%d", downloadedFiles.get(), totalFiles.get()));
                }
                frame.repaint();
            }catch (InterruptedException e){
                break;
            }
        }
        try(FileOutputStream out = new FileOutputStream(directory+"/"+versionManifest.getString("name")+"/adaptmeta.json")){
            out.write(new MapTree()
                            .put("packVersion", versionManifest.getString("packVersion", "unknown"))
                    .toJson().getBytes());
        }
        frame.dispose();
        System.out.println("Done downloading!");
        System.out.println("Done installing!");
        threadPool.shutdown();
        downloader.shutdown();
    }

    public static void main(String[] args) {
        FlatMacLightLaf.setup();

        new MinecraftInstaller(MapTree.fromJson(
                "{\"name\": \"testing_version\", " +
                "\"installation\": [" +
                    "{\"type\": \"installBase\", \"version\": \"fabric-loader-0.14.13-1.19.3\"}" +
                "]}"
        )
        ).install("FastLauncher");
    }
}
