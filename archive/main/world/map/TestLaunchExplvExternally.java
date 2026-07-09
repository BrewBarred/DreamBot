//package main.world.map;
//
//import org.osbot.rs07.script.Script;
//import org.osbot.rs07.script.ScriptManifest;
//
//import java.io.File;
//import java.util.Arrays;
//
//@ScriptManifest(
//        name = "Test: Explv's Map",
//        author = "ETA",
//        version = 0.1,
//        info = "Resolves BAT path from user.dir using relative paths, then passes baseDir to BAT.",
//        logo = ""
//)
//public class TestLaunchExplvExternally extends Script {
//    @Override
//    public void onStart() {
//        try {
//            File baseDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
//
//            // BAT stored alongside your map launcher code (relative to baseDir)
//            File bat = new File(baseDir, "DreamBotMan\\scripts\\main\\world\\map\\run_map.bat").getAbsoluteFile();
//
//            log("baseDir=" + baseDir);
//            log("bat=" + bat);
//
//            new ProcessBuilder(Arrays.asList(
//                    "cmd.exe", "/c",
//                    "start", "\"\"",
//                    bat.getAbsolutePath(),
//                    baseDir.getAbsolutePath()
//            )).start();
//
//            log("BAT launched.");
//        } catch (Throwable t) {
//            log("FAILED: " + t);
//            t.printStackTrace();
//        }
//    }
//
//    @Override
//    public int onLoop() {
//        return 200;
//    }
//}
