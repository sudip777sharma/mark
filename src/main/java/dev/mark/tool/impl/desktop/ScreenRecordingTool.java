package dev.mark.tool.impl.desktop;

import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tool for recording the Windows desktop screen and microphone audio into an MP4 video file.
 *
 * <p>Uses DirectShow audio capture and GDI screen frame capture streamed directly into FFmpeg
 * to produce H.264/AAC MP4 video files fully compatible with WhatsApp Web status upload
 * and desktop media players.
 */
@Component
public class ScreenRecordingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ScreenRecordingTool.class);

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private Process activeProcess;
    private Path activeOutputPath;
    private Path stopSignalPath;
    private Path activeScriptPath;
    private Path activeLogPath;
    private Instant recordingStartTime;
    private int recordingMaxDurationSeconds;

    @Override
    public String name() {
        return "screen_recorder";
    }

    @Override
    public String description() {
        return "Record the Windows desktop screen and microphone audio into an MP4 video file. "
             + "Actions: 'start_recording' (begins background screen & audio recording), "
             + "'stop_recording' (stops active recording and returns the saved .mp4 file path), "
             + "'status' (checks if a recording is currently in progress). "
             + "Parameters: 'duration_seconds' (optional max duration in seconds, default 30, max 300), "
             + "'fps' (optional framerate, default 15), 'include_audio' (boolean, default true), "
             + "'audio_device' (optional DirectShow audio device name, auto-detected by default), "
             + "'output_path' (optional destination file path, defaults to mark-workspace/recordings/screen_record_<timestamp>.mp4).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "One of: start_recording, stop_recording, status"),
                        "duration_seconds", Map.of(
                                "type", "integer",
                                "description", "Maximum recording duration in seconds (default 30, max 300)"),
                        "fps", Map.of(
                                "type", "integer",
                                "description", "Recording frame rate in frames per second (default 15, range 5-30)"),
                        "include_audio", Map.of(
                                "type", "boolean",
                                "description", "Whether to capture microphone audio (default true)"),
                        "audio_device", Map.of(
                                "type", "string",
                                "description", "Optional DirectShow audio device name (auto-detected if omitted)"),
                        "output_path", Map.of(
                                "type", "string",
                                "description", "Optional destination .mp4 file path (e.g. 'mark-workspace/recordings/demo.mp4')")),
                "required", List.of("action"));
    }

    @Override
    public synchronized ToolResultDTO execute(ToolRequestDTO request) {
        String action = (String) request.arguments().get("action");
        if (action == null || action.isBlank()) {
            return new ToolResultDTO(false, "screen_recorder requires an 'action' argument", Map.of());
        }

        return switch (action.toLowerCase()) {
            case "start_recording" -> handleStartRecording(request);
            case "stop_recording" -> handleStopRecording(request);
            case "status" -> handleStatus(request);
            default -> new ToolResultDTO(false, "Unknown screen_recorder action: " + action + ". Allowed: start_recording, stop_recording, status", Map.of());
        };
    }

    private ToolResultDTO handleStartRecording(ToolRequestDTO request) {
        if (isRecording.get() && activeProcess != null && activeProcess.isAlive()) {
            return new ToolResultDTO(false, "A screen recording is already in progress: " + activeOutputPath
                    + ". Call 'stop_recording' before starting a new recording.",
                    Map.of("status", "ALREADY_RECORDING", "file_path", activeOutputPath != null ? activeOutputPath.toString() : ""));
        }

        String ffmpegPath = resolveFfmpegPath();
        if (ffmpegPath == null) {
            return new ToolResultDTO(false, "FFmpeg executable not found on the system. Please ensure FFmpeg is installed and accessible.", Map.of());
        }

        int durationSec = 30;
        if (request.arguments().containsKey("duration_seconds")) {
            Object dObj = request.arguments().get("duration_seconds");
            if (dObj instanceof Number n) {
                durationSec = Math.max(1, Math.min(300, n.intValue()));
            }
        }

        int fps = 15;
        if (request.arguments().containsKey("fps")) {
            Object fpsObj = request.arguments().get("fps");
            if (fpsObj instanceof Number n) {
                fps = Math.max(5, Math.min(30, n.intValue()));
            }
        }

        boolean includeAudio = true;
        if (request.arguments().containsKey("include_audio")) {
            Object aObj = request.arguments().get("include_audio");
            if (aObj instanceof Boolean b) {
                includeAudio = b;
            } else if (aObj != null) {
                includeAudio = Boolean.parseBoolean(aObj.toString());
            }
        }

        String audioDevice = (String) request.arguments().get("audio_device");

        String outputPathStr = (String) request.arguments().get("output_path");
        Path outputPath = resolveOutputPath(outputPathStr);

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.deleteIfExists(outputPath);

            Path logsDir = Paths.get("mark-workspace", "logs");
            Files.createDirectories(logsDir);
            activeLogPath = logsDir.resolve("screen_recorder_" + System.currentTimeMillis() + ".log");

            stopSignalPath = Files.createTempFile("mark_rec_stop_", ".flag");
            Files.deleteIfExists(stopSignalPath); // Must not exist at start

            String scriptContent = generatePowerShellRecordingScript(
                    ffmpegPath,
                    outputPath.toAbsolutePath().toString(),
                    stopSignalPath.toAbsolutePath().toString(),
                    audioDevice,
                    includeAudio,
                    fps,
                    durationSec
            );

            activeScriptPath = Files.createTempFile("mark_screen_recorder_", ".ps1");
            Files.writeString(activeScriptPath, scriptContent, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", activeScriptPath.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(activeLogPath.toFile());

            activeProcess = pb.start();
            activeOutputPath = outputPath.toAbsolutePath();
            recordingStartTime = Instant.now();
            recordingMaxDurationSeconds = durationSec;
            isRecording.set(true);

            // Wait brief moment to confirm process didn't crash on start
            boolean exitedPrematurely = activeProcess.waitFor(600, TimeUnit.MILLISECONDS);
            if (exitedPrematurely && activeProcess.exitValue() != 0) {
                String logContent = Files.exists(activeLogPath) ? Files.readString(activeLogPath, StandardCharsets.UTF_8).trim() : "Unknown error";
                isRecording.set(false);
                cleanupTempFiles();
                return new ToolResultDTO(false, "Screen recording failed to initialize (exit code " + activeProcess.exitValue() + "): " + logContent, Map.of());
            }

            log.info("Screen recording started successfully. Output: {}, MaxDuration: {}s", activeOutputPath, durationSec);
            return new ToolResultDTO(true, "Screen recording started successfully. Output: " + activeOutputPath
                    + ". Max duration: " + durationSec + "s. Use action 'stop_recording' when finished.",
                    Map.of(
                            "action", "start_recording",
                            "status", "RECORDING",
                            "file_path", activeOutputPath.toString(),
                            "duration_seconds", durationSec,
                            "fps", fps,
                            "include_audio", includeAudio
                    ));

        } catch (Exception e) {
            log.error("Failed to start screen recording", e);
            isRecording.set(false);
            cleanupTempFiles();
            return new ToolResultDTO(false, "Failed to start screen recording: " + e.getMessage(), Map.of());
        }
    }

    private ToolResultDTO handleStopRecording(ToolRequestDTO request) {
        if (!isRecording.get() && (activeProcess == null || !activeProcess.isAlive())) {
            if (activeOutputPath != null && Files.exists(activeOutputPath)) {
                try {
                    long size = Files.size(activeOutputPath);
                    if (size > 1024) {
                        return new ToolResultDTO(true, "Screen recording is completed. Video saved to: " + activeOutputPath,
                                Map.of("action", "stop_recording", "status", "STOPPED", "file_path", activeOutputPath.toString(), "size_bytes", size));
                    }
                } catch (IOException ignored) {}
            }
            return new ToolResultDTO(false, "No screen recording is currently active.",
                    Map.of("action", "stop_recording", "status", "IDLE"));
        }

        try {
            // Send stop signal
            if (stopSignalPath != null) {
                Files.writeString(stopSignalPath, "STOP", StandardCharsets.UTF_8);
            }

            boolean finished = false;
            if (activeProcess != null) {
                finished = activeProcess.waitFor(8, TimeUnit.SECONDS);
                if (!finished) {
                    activeProcess.destroy();
                    finished = activeProcess.waitFor(2, TimeUnit.SECONDS);
                    if (!finished) {
                        activeProcess.destroyForcibly();
                    }
                }
            }

            isRecording.set(false);
            long elapsed = recordingStartTime != null ? Duration.between(recordingStartTime, Instant.now()).toSeconds() : 0;

            if (activeOutputPath != null && Files.exists(activeOutputPath) && Files.size(activeOutputPath) > 1024) {
                long sizeBytes = Files.size(activeOutputPath);
                log.info("Screen recording stopped successfully. File: {}, Size: {} bytes, Duration: {}s", activeOutputPath, sizeBytes, elapsed);
                cleanupTempFiles();
                return new ToolResultDTO(true, "Screen recording stopped successfully. Saved " + elapsed + "s video ("
                        + sizeBytes + " bytes) to: " + activeOutputPath,
                        Map.of(
                                "action", "stop_recording",
                                "status", "STOPPED",
                                "file_path", activeOutputPath.toString(),
                                "size_bytes", sizeBytes,
                                "duration_seconds", elapsed
                        ));
            } else {
                String logContent = (activeLogPath != null && Files.exists(activeLogPath))
                        ? Files.readString(activeLogPath, StandardCharsets.UTF_8).trim()
                        : "Output file was not generated.";
                cleanupTempFiles();
                return new ToolResultDTO(false, "Screen recording stopped but output video file was not generated or is empty. Details:\n" + logContent, Map.of());
            }

        } catch (Exception e) {
            log.error("Failed to stop screen recording", e);
            isRecording.set(false);
            cleanupTempFiles();
            return new ToolResultDTO(false, "Failed to stop screen recording: " + e.getMessage(), Map.of());
        }
    }

    private ToolResultDTO handleStatus(ToolRequestDTO request) {
        boolean active = isRecording.get() && activeProcess != null && activeProcess.isAlive();
        long elapsed = (active && recordingStartTime != null) ? Duration.between(recordingStartTime, Instant.now()).toSeconds() : 0;

        return new ToolResultDTO(true, "Screen recording status: " + (active ? "RECORDING" : "IDLE"),
                Map.of(
                        "action", "status",
                        "recording", active,
                        "status", active ? "RECORDING" : "IDLE",
                        "file_path", activeOutputPath != null ? activeOutputPath.toString() : "",
                        "elapsed_seconds", elapsed,
                        "max_duration_seconds", recordingMaxDurationSeconds
                ));
    }

    private Path resolveOutputPath(String outputPathStr) {
        if (outputPathStr == null || outputPathStr.isBlank()) {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            return Paths.get("mark-workspace", "recordings", "screen_record_" + timestamp + ".mp4");
        }

        String path = outputPathStr.trim();
        if (!path.toLowerCase().endsWith(".mp4")) {
            path += ".mp4";
        }

        // Handle OneDrive/Desktop path alias
        Path desktopResolved = resolveDesktopPath(path);
        if (desktopResolved != null) {
            return desktopResolved;
        }

        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return Paths.get("mark-workspace").resolve(p);
    }

    private Path resolveDesktopPath(String pathStr) {
        String stripped = pathStr.replace('\\', '/');
        if (stripped.startsWith("Desktop/")) {
            stripped = stripped.substring(8);
        } else if (stripped.startsWith("./Desktop/")) {
            stripped = stripped.substring(10);
        } else {
            return null;
        }

        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            Path oneDriveDesktop = Paths.get(userHome, "OneDrive", "Desktop").resolve(stripped).normalize();
            if (Files.exists(oneDriveDesktop.getParent())) {
                return oneDriveDesktop;
            }
            Path stdDesktop = Paths.get(userHome, "Desktop").resolve(stripped).normalize();
            if (Files.exists(stdDesktop.getParent())) {
                return stdDesktop;
            }
        }
        return null;
    }

    private String resolveFfmpegPath() {
        // 1. Check if ffmpeg is in system PATH
        try {
            Process p = new ProcessBuilder("where.exe", "ffmpeg").start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String[] lines = out.split("\\r?\\n");
                if (lines.length > 0 && !lines[0].isBlank()) {
                    return lines[0].trim();
                }
            }
        } catch (Exception ignored) {}

        // 2. Check WinGet Packages directory
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            Path wingetDir = Paths.get(localAppData, "Microsoft", "WinGet", "Packages");
            if (Files.exists(wingetDir)) {
                try (var stream = Files.walk(wingetDir, 4)) {
                    var ffmpegFile = stream
                            .filter(f -> f.getFileName().toString().equalsIgnoreCase("ffmpeg.exe"))
                            .findFirst();
                    if (ffmpegFile.isPresent()) {
                        return ffmpegFile.get().toAbsolutePath().toString();
                    }
                } catch (IOException ignored) {}
            }
        }

        // 3. Fallback to known Gyan.FFmpeg essentials path
        if (localAppData != null) {
            Path fallback = Paths.get(localAppData, "Microsoft", "WinGet", "Packages",
                    "Gyan.FFmpeg.Essentials_Microsoft.Winget.Source_8wekyb3d8bbwe",
                    "ffmpeg-9.0.1-essentials_build", "bin", "ffmpeg.exe");
            if (Files.exists(fallback)) {
                return fallback.toAbsolutePath().toString();
            }
        }

        return null;
    }

    private void cleanupTempFiles() {
        if (stopSignalPath != null) {
            try { Files.deleteIfExists(stopSignalPath); } catch (Exception ignored) {}
        }
        if (activeScriptPath != null) {
            try { Files.deleteIfExists(activeScriptPath); } catch (Exception ignored) {}
        }
    }

    private String generatePowerShellRecordingScript(
            String ffmpegPath,
            String outputPath,
            String stopSignalPath,
            String audioDevice,
            boolean includeAudio,
            int fps,
            int maxDurationSec
    ) {
        String cleanFfmpeg = ffmpegPath.replace("'", "''");
        String cleanOutput = outputPath.replace("'", "''");
        String cleanStopSignal = stopSignalPath.replace("'", "''");
        String cleanAudioDevice = audioDevice != null ? audioDevice.replace("'", "''") : "";

        return String.join("\n",
                "Add-Type -AssemblyName System.Windows.Forms",
                "Add-Type -AssemblyName System.Drawing",
                "",
                "Add-Type @'",
                "using System;",
                "using System.IO;",
                "using System.Drawing;",
                "using System.Drawing.Imaging;",
                "using System.Runtime.InteropServices;",
                "using System.Threading;",
                "using System.Diagnostics;",
                "",
                "public class NativeDesktopRecorder {",
                "    [DllImport(\"user32.dll\", SetLastError = true)]",
                "    public static extern IntPtr OpenInputDesktop(uint dwFlags, bool fInherit, uint dwDesiredAccess);",
                "",
                "    [DllImport(\"user32.dll\", SetLastError = true)]",
                "    public static extern bool SetThreadDesktop(IntPtr hDesktop);",
                "",
                "    [DllImport(\"user32.dll\", SetLastError = true)]",
                "    public static extern bool CloseDesktop(IntPtr hDesktop);",
                "",
                "    public static int Record(string ffmpegPath, string audioDevice, string outputPath, string stopSignalPath, int width, int height, int fps, int maxDurationSec) {",
                "        int exitCode = -1;",
                "        Thread t = new Thread(() => {",
                "            IntPtr hDesk = OpenInputDesktop(0, false, 0x01FF);",
                "            bool setOk = SetThreadDesktop(hDesk);",
                "            Console.WriteLine(\"SetThreadDesktop: \" + setOk);",
                "",
                "            string args = string.Format(\"-y -f rawvideo -pix_fmt bgr24 -s {0}x{1} -framerate {2} -i - \", width, height, fps);",
                "            if (!string.IsNullOrEmpty(audioDevice)) {",
                "                args += string.Format(\"-f dshow -i \\\"audio={0}\\\" -c:a aac -b:a 128k \", audioDevice);",
                "            }",
                "            args += string.Format(\"-c:v libx264 -preset ultrafast -pix_fmt yuv420p -shortest \\\"{0}\\\"\", outputPath);",
                "            Console.WriteLine(\"FFmpeg args: \" + args);",
                "",
                "            ProcessStartInfo psi = new ProcessStartInfo(ffmpegPath, args);",
                "            psi.UseShellExecute = false;",
                "            psi.RedirectStandardInput = true;",
                "            psi.RedirectStandardError = true;",
                "",
                "            using (Process p = Process.Start(psi)) {",
                "                using (Stream stdin = p.StandardInput.BaseStream) {",
                "                    using (Bitmap bmp = new Bitmap(width, height, PixelFormat.Format24bppRgb)) {",
                "                        using (Graphics g = Graphics.FromImage(bmp)) {",
                "                            int totalFrames = fps * maxDurationSec;",
                "                            int frameIntervalMs = 1000 / fps;",
                "                            byte[] buffer = new byte[width * height * 3];",
                "                            Stopwatch sw = Stopwatch.StartNew();",
                "",
                "                            for (int i = 0; i < totalFrames; i++) {",
                "                                if (File.Exists(stopSignalPath)) {",
                "                                    Console.WriteLine(\"Stop signal received at frame \" + i);",
                "                                    break;",
                "                                }",
                "                                long frameStart = sw.ElapsedMilliseconds;",
                "                                g.CopyFromScreen(0, 0, 0, 0, new Size(width, height));",
                "                                BitmapData data = bmp.LockBits(new Rectangle(0, 0, width, height), ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);",
                "                                Marshal.Copy(data.Scan0, buffer, 0, buffer.Length);",
                "                                bmp.UnlockBits(data);",
                "",
                "                                try {",
                "                                    stdin.Write(buffer, 0, buffer.Length);",
                "                                } catch {",
                "                                    break;",
                "                                }",
                "",
                "                                long elapsed = sw.ElapsedMilliseconds - frameStart;",
                "                                int sleepTime = frameIntervalMs - (int)elapsed;",
                "                                if (sleepTime > 0) {",
                "                                    Thread.Sleep(sleepTime);",
                "                                }",
                "                            }",
                "                        }",
                "                    }",
                "                }",
                "                string stderr = p.StandardError.ReadToEnd();",
                "                p.WaitForExit();",
                "                exitCode = p.ExitCode;",
                "                if (File.Exists(stopSignalPath)) {",
                "                    try { File.Delete(stopSignalPath); } catch {}",
                "                }",
                "                Console.WriteLine(\"FFmpeg exitCode: \" + exitCode);",
                "                if (exitCode != 0) {",
                "                    Console.WriteLine(\"FFmpeg stderr: \" + stderr);",
                "                }",
                "            }",
                "            CloseDesktop(hDesk);",
                "        });",
                "        t.SetApartmentState(ApartmentState.STA);",
                "        t.Start();",
                "        t.Join();",
                "        return exitCode;",
                "    }",
                "}",
                "'@ -ReferencedAssemblies System.Drawing",
                "",
                "$ffmpeg = '" + cleanFfmpeg + "'",
                "$output = '" + cleanOutput + "'",
                "$stop = '" + cleanStopSignal + "'",
                "$audioDev = '" + cleanAudioDevice + "'",
                "$includeAudio = " + (includeAudio ? "$true" : "$false"),
                "$fps = " + fps,
                "$maxDur = " + maxDurationSec,
                "",
                "# Determine primary screen dimensions (ensuring even integers)",
                "$w = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width",
                "$h = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height",
                "$w = [int]([Math]::Floor($w / 2.0) * 2)",
                "$h = [int]([Math]::Floor($h / 2.0) * 2)",
                "",
                "# Determine DirectShow audio device if enabled",
                "$selectedAudio = ''",
                "if ($includeAudio) {",
                "    if ($audioDev -and $audioDev.Trim()) {",
                "        $selectedAudio = $audioDev.Trim()",
                "    } else {",
                "        $dshowOut = & $ffmpeg -list_devices true -f dshow -i dummy 2>&1",
                "        $match = [regex]::Match($dshowOut, '\"([^\"]+)\"\\s*\\(audio\\)')",
                "        if ($match.Success) {",
                "            $selectedAudio = $match.Groups[1].Value",
                "            Write-Output \"Auto-detected audio device: $selectedAudio\"",
                "        } else {",
                "            Write-Output \"No DirectShow audio device found; recording video only.\"",
                "        }",
                "    }",
                "}",
                "",
                "$res = [NativeDesktopRecorder]::Record($ffmpeg, $selectedAudio, $output, $stop, $w, $h, $fps, $maxDur)",
                "exit $res"
        );
    }
}
