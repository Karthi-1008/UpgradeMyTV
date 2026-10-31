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
}

$JAVAC = "javac.exe"

Write-Host "Using Android Build Tools: $BUILD_TOOLS"
Write-Host "Using Android Platform:   $ANDROID_JAR"
Write-Host "Using Keystore:           $KEYSTORE"

Write-Host "=== 1. Cleaning and preparing build directories ==="
if (Test-Path "tvpowernew_src\bin") { Remove-Item "tvpowernew_src\bin" -Recurse -Force }
New-Item -ItemType Directory -Path "tvpowernew_src\bin" -Force | Out-Null
New-Item -ItemType Directory -Path "tvpowernew_src\dex" -Force | Out-Null

Write-Host "=== 2. Compiling Java sources with javac ==="
$javaFiles = Get-ChildItem -Path "tvpowernew_src\src", "tvpowernew_src\gen" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
Write-Host "Compiling $($javaFiles.Count) Java files..."
& $JAVAC -encoding UTF-8 -cp $ANDROID_JAR -d "tvpowernew_src\bin" $javaFiles

Write-Host "=== 3. Converting classes to dex with d8 ==="
$classFiles = Get-ChildItem -Path "tvpowernew_src\bin" -Recurse -Filter "*.class" | Select-Object -ExpandProperty FullName
& "$BUILD_TOOLS\d8.bat" --lib $ANDROID_JAR --output "tvpowernew_src\dex" $classFiles

Write-Host "=== 4. Packaging resources and assets with aapt2 ==="
if (Test-Path "compiled_res.zip") { Remove-Item "compiled_res.zip" -Force }
& "$BUILD_TOOLS\aapt2.exe" compile --dir "tvpowernew_src\res" -o compiled_res.zip
if (Test-Path "unaligned.apk") { Remove-Item "unaligned.apk" -Force }
& "$BUILD_TOOLS\aapt2.exe" link -o unaligned.apk -I $ANDROID_JAR -A "tvpowernew_src\assets" --manifest "tvpowernew_src\AndroidManifest.xml" compiled_res.zip

Write-Host "=== 5. Adding classes.dex to APK ==="
python -c "
import zipfile
with zipfile.ZipFile('unaligned.apk', 'a') as zf:
    zf.write('tvpowernew_src/dex/classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('classes.dex added!')
"

Write-Host "=== 6. Zipaligning APK ==="
if (Test-Path "TVPowerNew_aligned.apk") { Remove-Item "TVPowerNew_aligned.apk" -Force }
& "$BUILD_TOOLS\zipalign.exe" -p -f -v 4 unaligned.apk TVPowerNew_aligned.apk | Out-Null

Write-Host "=== 7. Signing APK with apksigner ==="
if (Test-Path "TVPowerNew.apk") { Remove-Item "TVPowerNew.apk" -Force }
& "$BUILD_TOOLS\apksigner.bat" sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --out TVPowerNew.apk TVPowerNew_aligned.apk
& "$BUILD_TOOLS\apksigner.bat" verify TVPowerNew.apk

Write-Host "=== SUCCESS: TVPowerNew.apk successfully built and verified! ==="
Get-Item TVPowerNew.apk | Select-Object Name, Length, LastWriteTime
