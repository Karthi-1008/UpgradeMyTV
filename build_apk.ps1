$ErrorActionPreference = "Stop"

# Auto-locate Android SDK
$ANDROID_SDK = if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $env:ANDROID_SDK_ROOT
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
    "$env:LOCALAPPDATA\Android\Sdk"
} else {
    throw "Android SDK not found. Please set ANDROID_HOME environment variable."
}

# Auto-locate build-tools
$buildToolsDirs = Get-ChildItem "$ANDROID_SDK\build-tools" -ErrorAction SilentlyContinue | Sort-Object Name -Descending
if (-not $buildToolsDirs) {
    throw "No build-tools found in $ANDROID_SDK\build-tools."
}
$BUILD_TOOLS = $buildToolsDirs[0].FullName

# Auto-locate android.jar
$platformDirs = Get-ChildItem "$ANDROID_SDK\platforms\*\android.jar" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending
if (-not $platformDirs) {
    throw "No android.jar found in $ANDROID_SDK\platforms."
}
$ANDROID_JAR = $platformDirs[0].FullName

# Auto-locate or generate debug keystore
$KEYSTORE = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $KEYSTORE)) {
    Write-Host "Creating debug keystore..."
    & keytool -genkey -v -keystore $KEYSTORE -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "Keytool failed" }
}

$JAVAC = "javac.exe"

Write-Host "Using Android Build Tools: $BUILD_TOOLS"
Write-Host "Using Android Platform:   $ANDROID_JAR"
Write-Host "Using Keystore:           $KEYSTORE"

Write-Host "=== 1. Cleaning and preparing build directories ==="
if (Test-Path "tvpowernew_src\bin") { Remove-Item "tvpowernew_src\bin" -Recurse -Force }
if (Test-Path "tvpowernew_src\dex") { Remove-Item "tvpowernew_src\dex" -Recurse -Force }
if (Test-Path "tvpowernew_src\gen") { Remove-Item "tvpowernew_src\gen" -Recurse -Force }
New-Item -ItemType Directory -Path "tvpowernew_src\bin" -Force | Out-Null
New-Item -ItemType Directory -Path "tvpowernew_src\dex" -Force | Out-Null
New-Item -ItemType Directory -Path "tvpowernew_src\gen" -Force | Out-Null

# Auto-sync latest web assets
if (Test-Path "web") {
    Write-Host "Syncing web assets to tvpowernew_src\assets\web..."
    if (Test-Path "tvpowernew_src\assets\web") { Remove-Item "tvpowernew_src\assets\web" -Recurse -Force }
    New-Item -ItemType Directory -Path "tvpowernew_src\assets\web" -Force | Out-Null
    Copy-Item -Path "web\*" -Destination "tvpowernew_src\assets\web" -Recurse -Force
}

Write-Host "=== 2. Compiling resources and generating R.java with aapt2 ==="
if (Test-Path "compiled_res.zip") { Remove-Item "compiled_res.zip" -Force }
& "$BUILD_TOOLS\aapt2.exe" compile --dir "tvpowernew_src\res" -o compiled_res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed with exit code $LASTEXITCODE" }

& "$BUILD_TOOLS\aapt2.exe" link -o temp_res.apk -I $ANDROID_JAR --manifest "tvpowernew_src\AndroidManifest.xml" --java "tvpowernew_src\gen" compiled_res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed with exit code $LASTEXITCODE" }
if (Test-Path "temp_res.apk") { Remove-Item "temp_res.apk" -Force }

Write-Host "=== 3. Compiling Java sources with javac ==="
$javaFiles = Get-ChildItem -Path "tvpowernew_src\src", "tvpowernew_src\gen" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
Write-Host "Compiling $($javaFiles.Count) Java files..."
& $JAVAC -encoding UTF-8 -cp $ANDROID_JAR -d "tvpowernew_src\bin" $javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac compilation failed with exit code $LASTEXITCODE" }

Write-Host "=== 4. Converting classes to dex with d8 ==="
$classFiles = Get-ChildItem -Path "tvpowernew_src\bin" -Recurse -Filter "*.class" | Select-Object -ExpandProperty FullName
& "$BUILD_TOOLS\d8.bat" --lib $ANDROID_JAR --output "tvpowernew_src\dex" $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 conversion failed with exit code $LASTEXITCODE" }

Write-Host "=== 5. Packaging final APK with aapt2 ==="
if (Test-Path "unaligned.apk") { Remove-Item "unaligned.apk" -Force }
& "$BUILD_TOOLS\aapt2.exe" link -o unaligned.apk -I $ANDROID_JAR -A "tvpowernew_src\assets" --manifest "tvpowernew_src\AndroidManifest.xml" compiled_res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 packaging failed with exit code $LASTEXITCODE" }

Write-Host "=== 6. Adding classes.dex to APK ==="
python -c "
import zipfile
with zipfile.ZipFile('unaligned.apk', 'a') as zf:
    zf.write('tvpowernew_src/dex/classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('classes.dex added!')
"

Write-Host "=== 7. Zipaligning APK ==="
if (Test-Path "TVPowerNew_aligned.apk") { Remove-Item "TVPowerNew_aligned.apk" -Force }
& "$BUILD_TOOLS\zipalign.exe" -p -f -v 4 unaligned.apk TVPowerNew_aligned.apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw "zipalign failed with exit code $LASTEXITCODE" }

Write-Host "=== 8. Signing APK with apksigner ==="
if (Test-Path "TVPowerNew.apk") { Remove-Item "TVPowerNew.apk" -Force }
& "$BUILD_TOOLS\apksigner.bat" sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --out TVPowerNew.apk TVPowerNew_aligned.apk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed with exit code $LASTEXITCODE" }
& "$BUILD_TOOLS\apksigner.bat" verify TVPowerNew.apk

Write-Host "=== 9. Compiling KeyDaemon companion ==="
& $JAVAC -encoding UTF-8 -source 1.8 -target 1.8 KeyDaemon.java
if ($LASTEXITCODE -ne 0) { throw "KeyDaemon javac failed" }
& "$BUILD_TOOLS\d8.bat" --output . KeyDaemon.class
if ($LASTEXITCODE -ne 0) { throw "KeyDaemon d8 failed" }
if (Test-Path "keydaemon.jar") { Remove-Item "keydaemon.jar" -Force }
jar cvf keydaemon.jar classes.dex
if (Test-Path "KeyDaemon.class") { Remove-Item "KeyDaemon.class" -Force }
if (Test-Path "classes.dex") { Remove-Item "classes.dex" -Force }

Write-Host "=== SUCCESS: TVPowerNew.apk and keydaemon.jar successfully built and verified! ==="
Get-Item TVPowerNew.apk, keydaemon.jar | Select-Object Name, Length, LastWriteTime
