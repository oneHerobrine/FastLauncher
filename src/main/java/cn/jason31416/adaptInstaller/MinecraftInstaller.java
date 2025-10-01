package cn.jason31416.adaptInstaller;

import dev.onelili.util.MapTree;
import lombok.NonNull;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloader;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloaderBuilder;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.CallbackAdapter;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.DownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.tasks.DownloadTask;
import org.to2mbn.jmccc.mcdownloader.provider.DownloadProviderChain;
import org.to2mbn.jmccc.mcdownloader.provider.fabric.FabricDownloadProvider;
import org.to2mbn.jmccc.mcdownloader.provider.forge.ForgeDownloadProvider;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.version.Version;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MinecraftInstaller {
    private ExecutorService threadPool;
    private final JFrame frame;
    private final MapTree versionManifest;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
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
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(50, 150, 255));

        statusLabel = new JLabel("下载文件中...", JLabel.CENTER);

        mainPanel.add(statusLabel, BorderLayout.NORTH);
        mainPanel.add(progressBar, BorderLayout.CENTER);

        frame.add(mainPanel);
    }

    private void downloadFile(@NonNull URL url, @NonNull File toFile) {
        threadPool.submit(() -> {
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
    }
    public void install(String directory){
        frame.setVisible(true);
        threadPool = Executors.newFixedThreadPool(versionManifest.getInt("threads", 4));
        System.out.println("Downloading version "+versionManifest.getString("name"));
        MinecraftDirectory dir = new MinecraftDirectory(directory+"/"+versionManifest.getString("name"));
        ForgeDownloadProvider forgeProvider = new ForgeDownloadProvider();
        FabricDownloadProvider fabricProvider = new FabricDownloadProvider();
        MinecraftDownloader downloader = MinecraftDownloaderBuilder
                .create()
                .providerChain(DownloadProviderChain.create().addProvider(forgeProvider).addProvider(fabricProvider))
                .build();
        Future<Version> future = downloader.downloadIncrementally(dir, versionManifest.getString("baseVersion"), new CallbackAdapter<Version>() {
            @Override
            public void failed(Throwable e) {
                // when the task fails
                e.printStackTrace();
                frame.dispose();
            }

            @Override
            public void done(Version result) {
                // when the task finishes
                System.out.println("Done downloading version "+versionManifest.getString("name"));
                frame.dispose();
            }

            @Override
            public void cancelled() {
                // when the task cancels
                System.out.println("Cancelled downloading version "+versionManifest.getString("name"));
                frame.dispose();
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
        if(versionManifest.contains("modification")){
            for(String path: versionManifest.getSection("modification").getKeys()){
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
                try {
                    downloadFile(new URL(versionManifest.getString("modification."+path)), file);
                } catch (MalformedURLException e) {
                    System.out.println("\033[31mError: Malformed URL: "+versionManifest.getString("modification."+path)+"!\033[0m");
                }
            }
        }
        while (downloadedFiles.get()<totalFiles.get()||!future.isDone()) {
            try {
                Thread.sleep(20);
                if(totalFiles.get()>0) {
                    progressBar.setValue(downloadedFiles.get() * 100 / totalFiles.get());
                    progressBar.setString(String.format("%d/%d", downloadedFiles.get(), totalFiles.get()));
                }
                frame.repaint();
            }catch (InterruptedException e){
                e.printStackTrace();
                break;
            }
        }
        System.out.println("Done downloading!");
        System.out.println("Done installing!");
        threadPool.shutdown();
        downloader.shutdown();
        while(frame.isActive()){
            try{
                Thread.sleep(50);
            }catch (InterruptedException e){
                break;
            }
        }
    }

    public static void main(String[] args) {
        new MinecraftInstaller(new MapTree()
                .put("name", "testing_version")
                .put("baseVersion", "fabric-loader-0.14.13-1.19.3")
        ).install("minecraft");
    }
}
