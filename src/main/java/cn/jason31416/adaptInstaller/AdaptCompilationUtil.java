package cn.jason31416.adaptInstaller;

import dev.onelili.util.MapTree;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.*;

public class AdaptCompilationUtil {
    private static Set<String> diffs = new HashSet<>();
    private static File oldDir, newDir;
    @SneakyThrows
    private static void compareAndRecord(String dir){
        for(File file : new File(newDir, dir).listFiles()){
            if(new File(oldDir, dir+"/"+file.getName()).exists()){
                if(file.isDirectory()){
                    compareAndRecord(dir+"/"+file.getName());
                }else{
                    byte[] oldB = Files.readAllBytes(new File(oldDir, dir+"/"+file.getName()).toPath());
                    byte[] newB = Files.readAllBytes(file.toPath());
                    if(!Arrays.equals(oldB, newB)){
                        diffs.add(dir + "/" + file.getName());
                        System.out.println("Diff found: " + dir + "/" + file.getName());
                    }
                }
            }else{
                if(file.isDirectory()){
                    compareAndRecord(dir+"/"+file.getName());
                }else {
                    diffs.add(dir + "/" + file.getName());
                    System.out.println("Diff found: " + dir + "/" + file.getName());
                }
            }
        }
    }

    private static void deleteFileDirectory(File file){
        if(file.isDirectory()){
            for(File f: file.listFiles()){
                deleteFileDirectory(f);
            }
        }else{
            file.delete();
        }
    }
    @SneakyThrows
    public static void main(String[] args) {
        String baseVersion = "fabric-loader-0.16.10-1.21.1",
                packageVersion = "1.0.0",
                packageName = "bmis";
        String oldPath = "FastLauncher/bmis-old", newPath = "FastLauncher/bmis";
        String baseUrl = "https://gitee.com/jason31416/files/raw/master/";
        long zipSize = 1024 * 1024 * 9; // 9MB

        oldDir = new File(oldPath);
        newDir = new File(newPath);
        compareAndRecord("");
        System.out.println("Total diff files: " + diffs.size());

        // flatten diff files

        System.out.println("Copying diff files to diff directory...");

        File diffDir = new File("adaptc/files");
        if(diffDir.exists()) {
            deleteFileDirectory(diffDir);
        }
        diffDir.mkdirs();

        Map<String, String> pathMap = new HashMap<>();
        for(String diff : diffs){
            String itemId = UUID.randomUUID().toString().replace("-", "");
            pathMap.put(diff, itemId);
            Files.copy(new File(newDir, diff).toPath(), new File(diffDir, itemId).toPath());
        }

        // compress diff files

        System.out.println("Compressing diff files...");

        String diffZipName = packageName + "-" + packageVersion + ".zip";

        // Compress using zip4j

        File targetZipDir = new File("adaptc/target");
        if(targetZipDir.exists()) {
            deleteFileDirectory(targetZipDir);
        }
        targetZipDir.mkdirs();

        ZipFile diffZip = new ZipFile(new File(targetZipDir, diffZipName));
        diffZip.createSplitZipFile(pathMap.entrySet().stream().map(e -> new File(diffDir, e.getValue())).toList(), new ZipParameters(), true, zipSize);

        System.out.println("Generating adapt.json...");

        // generate adapt.json
        Map<String, Object> adaptJson = new HashMap<>();
        adaptJson.put("name", packageName);
        adaptJson.put("packVersion", packageVersion);

        List<Map<String, Object>> installationScript = new ArrayList<>();
        installationScript.add(new MapTree()
                .put("type", "installBase")
                .put("version", baseVersion)
                .data);

        Map<String, Object> downloads = new HashMap<>();
        for(File i: targetZipDir.listFiles()){
            if(i.isFile()){
                downloads.put("adaptc/"+i.getName(), baseUrl + i.getName());
            }
        }
        installationScript.add(new MapTree()
                .put("type", "installPackage")
                .put("modifications", downloads)
                .data);
        installationScript.add(new MapTree()
                .put("type", "await")
                .data);
        installationScript.add(new MapTree()
                .put("type", "unzip")
                .put("file", "adaptc/"+diffZipName)
                .put("to", "adaptc")
                .data);
        installationScript.add(new MapTree()
                .put("type", "await")
                .data);
        for(String diff : diffs){
            installationScript.add(new MapTree()
                    .put("type", "move")
                    .put("from", "adaptc/"+pathMap.get(diff))
                    .put("to", diff.substring(1))
                    .data);
        }
        installationScript.add(new MapTree()
                .put("type", "await")
                .data);
        installationScript.add(new MapTree()
                .put("type", "delete")
                .put("file", "adaptc")
                .data);

        adaptJson.put("installation", installationScript);

        Files.write(new File(targetZipDir, "adapt.json").toPath(), new MapTree(adaptJson).toJson().getBytes());
        System.out.println("Done!");
    }
}
