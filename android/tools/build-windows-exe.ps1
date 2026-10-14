param(
    [string]$JdkPath = "C:\Program Files\Java\jdk-26.0.1",
    [string]$JbrPath = "C:\Program Files\Android\Android Studio\jbr"
)
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $javac = Join-Path $JbrPath "bin\javac.exe"
    $jarExe = Join-Path $JbrPath "bin\jar.exe"
    if (-not (Test-Path -LiteralPath $javac)) {
        $javac = Join-Path $JdkPath "bin\javac.exe"
        $jarExe = Join-Path $JdkPath "bin\jar.exe"
    }
    $jpackage = Join-Path $JdkPath "bin\jpackage.exe"

    $workDir = Join-Path $env:TEMP "iro_desktop_build"
    if (Test-Path -LiteralPath $workDir) {
        Remove-Item -LiteralPath $workDir -Recurse -Force
    }
    $classesDir = Join-Path $workDir "classes"
    $inputDir = Join-Path $workDir "input"
    $appImageParent = Join-Path $workDir "appimage"
    New-Item -ItemType Directory -Path $classesDir, $inputDir, $appImageParent -Force | Out-Null

    # Tạo file icon chuẩn Windows .ico (phong cách [Ir] Photoshop CC 2025)
    Add-Type -AssemblyName System.Drawing
    $icoPath = Join-Path $workDir "IROStudio.ico"
    $bmp = New-Object System.Drawing.Bitmap(64, 64)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 30, 54))
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 49, 168, 255), 3.0)
    $fgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 49, 168, 255))
    $g.FillRectangle($bgBrush, 2, 2, 60, 60)
    $g.DrawRectangle($borderPen, 3, 3, 58, 58)
    $font = New-Object System.Drawing.Font("Segoe UI", 26, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawString("Ir", $font, $fgBrush, 12, 13)
    $g.Dispose()

    $msPng = New-Object System.IO.MemoryStream
    $bmp.Save($msPng, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBytes = $msPng.ToArray()
    $msPng.Dispose()
    $bmp.Dispose()

    $fsIco = [System.IO.File]::OpenWrite($icoPath)
    $bw = New-Object System.IO.BinaryWriter($fsIco)
    $bw.Write([uint16]0)
    $bw.Write([uint16]1)
    $bw.Write([uint16]1)
    $bw.Write([byte]64)
    $bw.Write([byte]64)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([uint16]1)
    $bw.Write([uint16]32)
    $bw.Write([uint32]$pngBytes.Length)
    $bw.Write([uint32]22)
    $bw.Write($pngBytes)
    $bw.Dispose()
    $fsIco.Dispose()

    Write-Host "[1/5] Compiling shared Android ColorEngine + LightCraftEngine + Desktop Studio UI..."
    $sharedNames = @(
        "AutoBalanceGuard.java",
        "AutoBalancer.java",
        "ColorEngine.java",
        "CubeLut.java",
        "DebugLogger.java",
        "ExportSizing.java",
        "LightCraftEngine.java",
        "OnlineLuts.java",
        "Recipe.java",
        "Settings.java",
        "SettingsCodec.java"
    )
    $sharedSources = $sharedNames | ForEach-Object { "app/src/main/java/vn/iro/editor/$_" }
    $desktopSources = Get-ChildItem -LiteralPath "desktop/src" -Recurse -Filter "*.java" | ForEach-Object {
        $_.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
    }

    $allSources = $sharedSources + $desktopSources
    & $javac --release 17 -encoding UTF-8 -d $classesDir $allSources
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed with exit code $LASTEXITCODE"
    }

    Write-Host "[2/5] Bundling 392 Fuji X Weekly recipes & 52 sample photos into IROStudio.jar..."
    Copy-Item -LiteralPath (Join-Path $projectRoot "app\src\main\assets\recipes.json") -Destination (Join-Path $classesDir "recipes.json") -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "app\src\main\assets\sample-recipe.json") -Destination (Join-Path $classesDir "sample-recipe.json") -Force
    $fxwDest = Join-Path $classesDir "fujixweekly"
    New-Item -ItemType Directory -Path $fxwDest -Force | Out-Null
    Copy-Item -Path (Join-Path $projectRoot "app\src\main\assets\fujixweekly\*.jpg") -Destination $fxwDest -Force

    $jarFile = Join-Path $inputDir "IROStudio.jar"
    & $jarExe --create --file $jarFile --main-class vn.iro.editor.IroDesktopApp -C $classesDir .
    if ($LASTEXITCODE -ne 0) {
        throw "jar creation failed"
    }

    $distDir = Join-Path $projectRoot "desktop\dist"
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    Copy-Item -LiteralPath $jarFile -Destination (Join-Path $distDir "IROStudio.jar") -Force

    Write-Host "[3/5] Packaging native Windows .exe + embedded runtime with jpackage..."
    & $jpackage `
        --type app-image `
        --name "IROStudio" `
        --icon $icoPath `
        --input $inputDir `
        --main-jar "IROStudio.jar" `
        --main-class "vn.iro.editor.IroDesktopApp" `
        --add-modules "java.base,java.desktop,java.logging,jdk.crypto.ec" `
        --dest $appImageParent `
        --vendor "IRO Studio" `
        --app-version "1.0.0" `
        --description "IRO Studio CC 2025 - Fujifilm & LightCraft Photo Editor"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    # Tạo thêm trình gỡ cài đặt độc lập Uninstall-IROStudio.exe nằm ngay trong thư mục cài đặt
    $csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    $uninstallerCs = Join-Path $workDir "Uninstaller.cs"
    $uninstallerCode = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;
using Microsoft.Win32;

class IroUninstaller {
    private const string UNINSTALL_REG_KEY = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\IROStudio";

    [STAThread]
    static void Main(string[] args) {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        bool silent = false;
        foreach (string a in args) {
            if (a == "--silent" || a == "/S" || a == "/silent" || a == "--quiet") {
                silent = true;
            }
        }

        string installDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs", "IROStudio");

        if (!silent) {
            DialogResult dr = MessageBox.Show(
                "Bạn có chắc chắn muốn gỡ cài đặt hoàn toàn IRO Studio CC 2025 khỏi máy tính không?",
                "Gỡ cài đặt IRO Studio CC 2025",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);
            if (dr != DialogResult.Yes) {
                return;
            }
        }

        try {
            // 1. Đóng tiến trình IROStudio nếu đang mở
            foreach (Process p in Process.GetProcessesByName("IROStudio")) {
                try { p.Kill(); p.WaitForExit(2000); } catch { }
            }

            // 2. Xóa shortcut ngoài Desktop và Start Menu
            string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "IRO Studio.lnk");
            if (File.Exists(desktopLnk)) {
                try { File.Delete(desktopLnk); } catch { }
            }

            string startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "IRO Studio");
            if (Directory.Exists(startMenuDir)) {
                try { Directory.Delete(startMenuDir, true); } catch { }
            }

            // 3. Xóa khóa đăng ký trong Control Panel (Programs and Features / Installed Apps)
            try {
                Registry.CurrentUser.DeleteSubKeyTree(UNINSTALL_REG_KEY, false);
            } catch { }

            if (!silent) {
                MessageBox.Show(
                    "Đã gỡ cài đặt IRO Studio CC 2025 khỏi máy tính thành công.",
                    "Gỡ cài đặt hoàn tất",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
            }

            // 4. Tự xóa thư mục cài đặt sau khi tiến trình Uninstaller thoát
            if (Directory.Exists(installDir)) {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe",
                    "/c timeout /t 1 /nobreak >nul & rmdir /s /q \"" + installDir + "\"");
                psi.WindowStyle = ProcessWindowStyle.Hidden;
                psi.CreateNoWindow = true;
                psi.UseShellExecute = false;
                Process.Start(psi);
            }
        } catch (Exception ex) {
            if (!silent) {
                MessageBox.Show("Lỗi khi gỡ cài đặt: " + ex.Message, "Gỡ cài đặt IRO Studio", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
'@
    Set-Content -LiteralPath $uninstallerCs -Value $uninstallerCode -Encoding UTF8
    $uninstallerExe = Join-Path $appImageParent "IROStudio\Uninstall-IROStudio.exe"
    & $csc /target:winexe /out:$uninstallerExe /win32icon:$icoPath /r:System.dll /r:System.Windows.Forms.dll $uninstallerCs
    if ($LASTEXITCODE -ne 0) {
        throw "csc uninstaller build failed"
    }

    $portableDest = Join-Path $distDir "IROStudio"
    if (Test-Path -LiteralPath $portableDest) {
        Remove-Item -LiteralPath $portableDest -Recurse -Force
    }
    Copy-Item -LiteralPath (Join-Path $appImageParent "IROStudio") -Destination $portableDest -Recurse -Force

    Write-Host "[4/5] Creating compressed payload & single-file Windows Installer (IRO-Studio-Setup.exe) with Control Panel registration..."
    $payloadZip = Join-Path $workDir "IROStudioPayload.zip"
    Compress-Archive -Path (Join-Path $appImageParent "IROStudio\*") -DestinationPath $payloadZip -CompressionLevel Optimal -Force

    $installerCs = Join-Path $workDir "Installer.cs"
    $csCode = @'
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("IRO Studio CC 2025 Setup")]
[assembly: AssemblyDescription("Trinh cai dat IRO Studio CC 2025 - Fujifilm & LightCraft Photo Editor")]
[assembly: AssemblyCompany("IRO Studio")]
[assembly: AssemblyProduct("IRO Studio CC 2025")]
[assembly: AssemblyVersion("1.0.0.0")]

namespace IroInstaller {
    public class SetupForm : Form {
        private const string UNINSTALL_REG_KEY = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\IROStudio";
        private Label statusLbl;
        private ProgressBar progress;
        private Button installBtn;
        private Button uninstallBtn;
        private CheckBox desktopShortcutCb;
        private CheckBox launchAfterCb;
        private string installDir;

        public SetupForm(bool silent, bool uninstallMode) {
            installDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs", "IROStudio");

            if (uninstallMode) {
                PerformUninstall(silent);
                Environment.Exit(0);
            }

            if (silent) {
                PerformInstall(true, false);
                Environment.Exit(0);
            }

            Text = "Cài đặt IRO Studio CC 2025 · Fujifilm & LightCraft Photo Editor";
            Size = new Size(540, 330);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(29, 29, 29);
            ForeColor = Color.FromArgb(232, 232, 232);
            Font = new Font("Segoe UI", 10f, FontStyle.Regular);

            Label title = new Label();
            title.Text = "IRO STUDIO CC 2025 · WINDOWS DESKTOP";
            title.Font = new Font("Segoe UI", 14.5f, FontStyle.Bold);
            title.ForeColor = Color.FromArgb(49, 168, 255);
            title.Location = new Point(24, 20);
            title.AutoSize = true;
            Controls.Add(title);

            Label sub = new Label();
            sub.Text = "Trình chỉnh ảnh Fujifilm (392 công thức Fuji X Weekly) & LightCraft Engine.\nTích hợp đăng ký gỡ cài đặt trong Control Panel (Programs & Features).\nThư mục cài đặt: " + installDir;
            sub.ForeColor = Color.FromArgb(168, 168, 168);
            sub.Location = new Point(24, 52);
            sub.Size = new Size(485, 56);
            Controls.Add(sub);

            desktopShortcutCb = new CheckBox();
            desktopShortcutCb.Text = "Tạo biểu tượng IRO Studio ngoài màn hình Desktop & Start Menu";
            desktopShortcutCb.Checked = true;
            desktopShortcutCb.Location = new Point(26, 114);
            desktopShortcutCb.Size = new Size(465, 26);
            Controls.Add(desktopShortcutCb);

            launchAfterCb = new CheckBox();
            launchAfterCb.Text = "Khởi chạy IRO Studio CC 2025 ngay sau khi cài đặt xong";
            launchAfterCb.Checked = true;
            launchAfterCb.Location = new Point(26, 142);
            launchAfterCb.Size = new Size(465, 26);
            Controls.Add(launchAfterCb);

            progress = new ProgressBar();
            progress.Location = new Point(26, 180);
            progress.Size = new Size(472, 20);
            progress.Style = ProgressBarStyle.Continuous;
            Controls.Add(progress);

            statusLbl = new Label();
            statusLbl.Text = "Nhấn 'Cài đặt ngay' để cài đặt hoặc 'Gỡ cài đặt' để xóa khỏi máy.";
            statusLbl.ForeColor = Color.FromArgb(160, 160, 160);
            statusLbl.Location = new Point(26, 206);
            statusLbl.Size = new Size(472, 22);
            Controls.Add(statusLbl);

            uninstallBtn = new Button();
            uninstallBtn.Text = "Gỡ cài đặt";
            uninstallBtn.Font = new Font("Segoe UI", 10f, FontStyle.Regular);
            uninstallBtn.BackColor = Color.FromArgb(50, 50, 50);
            uninstallBtn.ForeColor = Color.FromArgb(230, 230, 230);
            uninstallBtn.FlatStyle = FlatStyle.Flat;
            uninstallBtn.Location = new Point(214, 238);
            uninstallBtn.Size = new Size(130, 36);
            uninstallBtn.Click += (s, e) => {
                PerformUninstall(false);
                Close();
            };
            Controls.Add(uninstallBtn);

            installBtn = new Button();
            installBtn.Text = "Cài đặt ngay";
            installBtn.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            installBtn.BackColor = Color.FromArgb(20, 115, 230);
            installBtn.ForeColor = Color.White;
            installBtn.FlatStyle = FlatStyle.Flat;
            installBtn.Location = new Point(354, 238);
            installBtn.Size = new Size(144, 36);
            installBtn.Click += async (s, e) => {
                installBtn.Enabled = false;
                uninstallBtn.Enabled = false;
                progress.Style = ProgressBarStyle.Marquee;
                statusLbl.Text = "Đang giải nén và đăng ký vào Windows Control Panel...";
                bool makeShortcut = desktopShortcutCb.Checked;
                bool launch = launchAfterCb.Checked;
                await Task.Run(() => PerformInstall(makeShortcut, false));
                progress.Style = ProgressBarStyle.Continuous;
                progress.Value = 100;
                statusLbl.Text = "Cài đặt hoàn tất!";
                if (launch) {
                    string exePath = Path.Combine(installDir, "IROStudio.exe");
                    if (File.Exists(exePath)) {
                        Process.Start(new ProcessStartInfo(exePath) { WorkingDirectory = installDir });
                    }
                }
                Close();
            };
            Controls.Add(installBtn);
        }

        private void PerformInstall(bool createShortcuts, bool launchNow) {
            try {
                foreach (Process p in Process.GetProcessesByName("IROStudio")) {
                    try { p.Kill(); p.WaitForExit(2000); } catch { }
                }

                if (Directory.Exists(installDir)) {
                    try { Directory.Delete(installDir, true); } catch { }
                }
                Directory.CreateDirectory(installDir);

                string tempZip = Path.Combine(Path.GetTempPath(), "iro_setup_payload_" + Guid.NewGuid().ToString("N") + ".zip");
                using (Stream res = Assembly.GetExecutingAssembly().GetManifestResourceStream("IROStudioPayload.zip"))
                using (FileStream fs = new FileStream(tempZip, FileMode.Create, FileAccess.Write)) {
                    res.CopyTo(fs);
                }

                ZipFile.ExtractToDirectory(tempZip, installDir);
                try { File.Delete(tempZip); } catch { }

                string exePath = Path.Combine(installDir, "IROStudio.exe");
                string uninstallerPath = Path.Combine(installDir, "Uninstall-IROStudio.exe");

                if (createShortcuts && File.Exists(exePath)) {
                    string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "IRO Studio.lnk");
                    string startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "IRO Studio");
                    Directory.CreateDirectory(startMenuDir);
                    string startMenuLnk = Path.Combine(startMenuDir, "IRO Studio.lnk");
                    string uninstallLnk = Path.Combine(startMenuDir, "Gỡ cài đặt IRO Studio.lnk");
                    CreateShortcut(desktopLnk, exePath, installDir, "IRO Studio CC 2025 - Fujifilm & LightCraft Photo Editor");
                    CreateShortcut(startMenuLnk, exePath, installDir, "IRO Studio CC 2025 - Fujifilm & LightCraft Photo Editor");
                    if (File.Exists(uninstallerPath)) {
                        CreateShortcut(uninstallLnk, uninstallerPath, installDir, "Gỡ cài đặt IRO Studio CC 2025 khỏi máy tính");
                    }
                }

                // Đăng ký vào Control Panel -> Programs and Features & Windows Installed Apps
                RegisterInControlPanel(exePath, uninstallerPath);

                if (launchNow && File.Exists(exePath)) {
                    Process.Start(new ProcessStartInfo(exePath) { WorkingDirectory = installDir });
                }
            } catch (Exception ex) {
                MessageBox.Show("Lỗi cài đặt: " + ex.Message, "IRO Studio Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void RegisterInControlPanel(string exePath, string uninstallerPath) {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_REG_KEY)) {
                if (key == null) return;
                key.SetValue("DisplayName", "IRO Studio CC 2025 (Fujifilm & LightCraft Photo Editor)", RegistryValueKind.String);
                key.SetValue("DisplayVersion", "1.0.0", RegistryValueKind.String);
                key.SetValue("Publisher", "IRO Studio", RegistryValueKind.String);
                key.SetValue("DisplayIcon", exePath + ",0", RegistryValueKind.String);
                key.SetValue("InstallLocation", installDir, RegistryValueKind.String);
                key.SetValue("InstallDate", DateTime.Now.ToString("yyyyMMdd"), RegistryValueKind.String);
                key.SetValue("UninstallString", "\"" + uninstallerPath + "\"", RegistryValueKind.String);
                key.SetValue("QuietUninstallString", "\"" + uninstallerPath + "\" --silent", RegistryValueKind.String);
                key.SetValue("EstimatedSize", 78500, RegistryValueKind.DWord);
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
        }

        private void PerformUninstall(bool silent) {
            string uninstallerPath = Path.Combine(installDir, "Uninstall-IROStudio.exe");
            if (File.Exists(uninstallerPath)) {
                ProcessStartInfo psi = new ProcessStartInfo(uninstallerPath, silent ? "--silent" : "");
                psi.WorkingDirectory = Path.GetTempPath();
                Process p = Process.Start(psi);
                if (silent && p != null) {
                    p.WaitForExit(10000);
                }
            } else {
                try { Registry.CurrentUser.DeleteSubKeyTree(UNINSTALL_REG_KEY, false); } catch { }
                if (!silent) {
                    MessageBox.Show("Ứng dụng chưa được cài đặt hoặc đã được gỡ bỏ trước đó.", "Gỡ cài đặt IRO Studio", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            }
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string workDir, string description) {
            try {
                Type t = Type.GetTypeFromProgID("WScript.Shell");
                dynamic shell = Activator.CreateInstance(t);
                dynamic lnk = shell.CreateShortcut(shortcutPath);
                lnk.TargetPath = targetPath;
                lnk.WorkingDirectory = workDir;
                lnk.Description = description;
                lnk.Save();
            } catch { }
        }

        [STAThread]
        public static void Main(string[] args) {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            bool silent = false;
            bool uninstall = false;
            foreach (string a in args) {
                if (a == "--silent" || a == "/S" || a == "/silent") silent = true;
                if (a == "--uninstall" || a == "/uninstall" || a == "/u") uninstall = true;
            }
            Application.Run(new SetupForm(silent, uninstall));
        }
    }
}
'@
    Set-Content -LiteralPath $installerCs -Value $csCode -Encoding UTF8

    $setupExeTemp = Join-Path $workDir "IRO-Studio-Setup.exe"
    $setupExeDist = Join-Path $distDir "IRO-Studio-Setup.exe"
    $setupExeRoot = Join-Path $projectRoot "IRO-Studio-Setup.exe"

    & $csc /target:winexe /out:$setupExeTemp /win32icon:$icoPath `
        /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll `
        /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll `
        /r:Microsoft.CSharp.dll `
        /resource:"$payloadZip,IROStudioPayload.zip" `
        $installerCs
    if ($LASTEXITCODE -ne 0) {
        throw "csc installer build failed"
    }
    Copy-Item -LiteralPath $setupExeTemp -Destination $setupExeDist -Force
    Copy-Item -LiteralPath $setupExeTemp -Destination $setupExeRoot -Force

    Write-Host "[5/5] Creating direct launcher IRO-Studio.exe in project root..."
    $launcherCs = Join-Path $workDir "Launcher.cs"
    $launcherCode = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

class IroLauncher {
    [STAThread]
    static void Main(string[] args) {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string portableExe = Path.Combine(baseDir, "desktop", "dist", "IROStudio", "IROStudio.exe");
        if (!File.Exists(portableExe)) {
            portableExe = Path.Combine(baseDir, "dist", "IROStudio", "IROStudio.exe");
        }
        string installedExe = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs", "IROStudio", "IROStudio.exe");

        string target = File.Exists(portableExe) ? portableExe : installedExe;
        if (File.Exists(target)) {
            ProcessStartInfo psi = new ProcessStartInfo(target, string.Join(" ", args));
            psi.WorkingDirectory = Path.GetDirectoryName(target);
            Process.Start(psi);
        } else {
            MessageBox.Show("Không tìm thấy IROStudio.exe. Hãy chạy IRO-Studio-Setup.exe trước.", "IRO Studio", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }
}
'@
    Set-Content -LiteralPath $launcherCs -Value $launcherCode -Encoding UTF8
    $launcherTemp = Join-Path $workDir "IRO-Studio.exe"
    $rootLauncher = Join-Path $projectRoot "IRO-Studio.exe"
    & $csc /target:winexe /out:$launcherTemp /win32icon:$icoPath /r:System.dll /r:System.Windows.Forms.dll $launcherCs
    if ($LASTEXITCODE -ne 0) {
        throw "csc launcher build failed"
    }
    Copy-Item -LiteralPath $launcherTemp -Destination $rootLauncher -Force

    Remove-Item -LiteralPath $workDir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "====================================================="
    Write-Host "BUILD COMPLETED SUCCESSFULLY!"
    Write-Host "1. Installer .exe : $setupExeRoot"
    Write-Host "2. Quick Launcher : $rootLauncher"
    Write-Host "3. Portable App   : $portableDest\IROStudio.exe"
    Write-Host "====================================================="
} finally {
    Pop-Location
}
