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

    Write-Host "[1/5] Compiling shared Android ColorEngine + LightCraftEngine + Desktop Studio UI..."
    $sharedNames = @(
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

    $distDir = Join-Path $projectRoot "dist"
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    Copy-Item -LiteralPath $jarFile -Destination (Join-Path $distDir "IROStudio.jar") -Force

    Write-Host "[3/5] Packaging native Windows .exe + embedded runtime with jpackage..."
    & $jpackage `
        --type app-image `
        --name "IROStudio" `
        --input $inputDir `
        --main-jar "IROStudio.jar" `
        --main-class "vn.iro.editor.IroDesktopApp" `
        --add-modules "java.base,java.desktop,java.logging,jdk.crypto.ec" `
        --dest $appImageParent `
        --vendor "IRO Studio" `
        --app-version "1.0.0" `
        --description "IRO Studio - Fujifilm Film Simulation & LightCraft Photo Editor"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    $portableDest = Join-Path $distDir "IROStudio"
    if (Test-Path -LiteralPath $portableDest) {
        Remove-Item -LiteralPath $portableDest -Recurse -Force
    }
    Copy-Item -LiteralPath (Join-Path $appImageParent "IROStudio") -Destination $portableDest -Recurse -Force

    Write-Host "[4/5] Creating compressed payload & single-file Windows Installer (IRO-Studio-Setup.exe)..."
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

[assembly: AssemblyTitle("IRO Studio Setup")]
[assembly: AssemblyDescription("Trinh cai dat IRO Studio - Fujifilm & LightCraft Photo Editor")]
[assembly: AssemblyCompany("IRO Studio")]
[assembly: AssemblyProduct("IRO Studio for Windows")]
[assembly: AssemblyVersion("1.0.0.0")]

namespace IroInstaller {
    public class SetupForm : Form {
        private Label statusLbl;
        private ProgressBar progress;
        private Button installBtn;
        private CheckBox desktopShortcutCb;
        private CheckBox launchAfterCb;
        private string installDir;

        public SetupForm(bool silent) {
            installDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs", "IROStudio");

            if (silent) {
                PerformInstall(true, false);
                Environment.Exit(0);
            }

            Text = "Cài đặt IRO Studio · Fujifilm & LightCraft Photo Editor";
            Size = new Size(520, 320);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(20, 23, 28);
            ForeColor = Color.FromArgb(241, 243, 244);
            Font = new Font("Segoe UI", 10f, FontStyle.Regular);

            Label title = new Label();
            title.Text = "IRO STUDIO · WINDOWS DESKTOP";
            title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
            title.ForeColor = Color.FromArgb(138, 180, 248);
            title.Location = new Point(24, 20);
            title.AutoSize = true;
            Controls.Add(title);

            Label sub = new Label();
            sub.Text = "Trình chỉnh ảnh Fujifilm (392 công thức Fuji X Weekly) & LightCraft Engine.\nThư mục cài đặt: " + installDir;
            sub.ForeColor = Color.FromArgb(165, 172, 182);
            sub.Location = new Point(24, 54);
            sub.Size = new Size(465, 44);
            Controls.Add(sub);

            desktopShortcutCb = new CheckBox();
            desktopShortcutCb.Text = "Tạo biểu tượng IRO Studio ngoài màn hình Desktop & Start Menu";
            desktopShortcutCb.Checked = true;
            desktopShortcutCb.Location = new Point(26, 108);
            desktopShortcutCb.Size = new Size(450, 26);
            Controls.Add(desktopShortcutCb);

            launchAfterCb = new CheckBox();
            launchAfterCb.Text = "Khởi chạy IRO Studio ngay sau khi cài đặt xong";
            launchAfterCb.Checked = true;
            launchAfterCb.Location = new Point(26, 138);
            launchAfterCb.Size = new Size(450, 26);
            Controls.Add(launchAfterCb);

            progress = new ProgressBar();
            progress.Location = new Point(26, 178);
            progress.Size = new Size(452, 22);
            progress.Style = ProgressBarStyle.Continuous;
            Controls.Add(progress);

            statusLbl = new Label();
            statusLbl.Text = "Nhấn 'Cài đặt ngay' để cài đặt IRO Studio vào máy tính.";
            statusLbl.ForeColor = Color.FromArgb(154, 160, 166);
            statusLbl.Location = new Point(26, 206);
            statusLbl.Size = new Size(452, 22);
            Controls.Add(statusLbl);

            installBtn = new Button();
            installBtn.Text = "Cài đặt ngay";
            installBtn.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            installBtn.BackColor = Color.FromArgb(42, 75, 124);
            installBtn.ForeColor = Color.White;
            installBtn.FlatStyle = FlatStyle.Flat;
            installBtn.Location = new Point(338, 234);
            installBtn.Size = new Size(140, 36);
            installBtn.Click += async (s, e) => {
                installBtn.Enabled = false;
                progress.Style = ProgressBarStyle.Marquee;
                statusLbl.Text = "Đang giải nén và cài đặt IRO Studio...";
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
                if (createShortcuts && File.Exists(exePath)) {
                    string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "IRO Studio.lnk");
                    string startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "IRO Studio");
                    Directory.CreateDirectory(startMenuDir);
                    string startMenuLnk = Path.Combine(startMenuDir, "IRO Studio.lnk");
                    CreateShortcut(desktopLnk, exePath, installDir);
                    CreateShortcut(startMenuLnk, exePath, installDir);
                }

                if (launchNow && File.Exists(exePath)) {
                    Process.Start(new ProcessStartInfo(exePath) { WorkingDirectory = installDir });
                }
            } catch (Exception ex) {
                MessageBox.Show("Lỗi cài đặt: " + ex.Message, "IRO Studio Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string workDir) {
            try {
                Type t = Type.GetTypeFromProgID("WScript.Shell");
                dynamic shell = Activator.CreateInstance(t);
                dynamic lnk = shell.CreateShortcut(shortcutPath);
                lnk.TargetPath = targetPath;
                lnk.WorkingDirectory = workDir;
                lnk.Description = "IRO Studio - Fujifilm & LightCraft Photo Editor";
                lnk.Save();
            } catch { }
        }

        [STAThread]
        public static void Main(string[] args) {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            bool silent = false;
            foreach (string a in args) {
                if (a == "--silent" || a == "/S" || a == "/silent") silent = true;
            }
            Application.Run(new SetupForm(silent));
        }
    }
}
'@
    Set-Content -LiteralPath $installerCs -Value $csCode -Encoding UTF8

    $csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    $setupExeTemp = Join-Path $workDir "IRO-Studio-Setup.exe"
    $setupExeDist = Join-Path $distDir "IRO-Studio-Setup.exe"
    $setupExeRoot = Join-Path $projectRoot "IRO-Studio-Setup.exe"

    & $csc /target:winexe /out:$setupExeTemp `
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
        string portableExe = Path.Combine(baseDir, "dist", "IROStudio", "IROStudio.exe");
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
    & $csc /target:winexe /out:$launcherTemp /r:System.dll /r:System.Windows.Forms.dll $launcherCs
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
