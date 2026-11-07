# Create complete package of TVPowerNew App with APK
$staging = "staging_zip\TVPowerNew"
if (Test-Path "staging_zip") {
    Remove-Item -Recurse -Force "staging_zip"
}
New-Item -ItemType Directory -Path $staging -Force | Out-Null

Write-Host "Staging release APK and companion..."
Copy-Item "TVPowerNew.apk" -Destination $staging
Copy-Item "keydaemon.jar" -Destination $staging
Copy-Item "build_apk.ps1" -Destination $staging
Copy-Item "KeyDaemon.java" -Destination $staging
Copy-Item "README.md" -Destination $staging
Copy-Item "LICENSE" -Destination $staging
Copy-Item "QUICK_START.txt" -Destination $staging

if (Test-Path "README_V2_ROADMAP.md") { Copy-Item "README_V2_ROADMAP.md" -Destination $staging }
if (Test-Path "AGENT_PORTING_GUIDE.md") { Copy-Item "AGENT_PORTING_GUIDE.md" -Destination $staging }

$tclJars = @('com.tcl.os.system.jar', 'remoteprovider.jar', 'tcl-framework.jar', 'tcl-service.jar', 'tvmanager.jar')
foreach ($jar in $tclJars) {
    if (Test-Path $jar) {
        Copy-Item $jar -Destination $staging
    }
}

if (Test-Path "tv_maintenance.bat") {
    Copy-Item "tv_maintenance.bat" -Destination $staging
}

Write-Host "Staging web frontend..."
Copy-Item -Path "web" -Destination $staging -Recurse

Write-Host "Staging Android app source..."
Copy-Item -Path "tvpowernew_src" -Destination $staging -Recurse

# Create Zip Archive
$zipPath = "TVPowerNew_Complete_App_with_APK.zip"
if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}

Write-Host "Compressing archive to $zipPath..."
Compress-Archive -Path "$staging\*" -DestinationPath $zipPath -CompressionLevel Optimal

Copy-Item $zipPath -Destination "TVPowerNew.zip" -Force

Write-Host "Zip created successfully!"
$zipItem = Get-Item $zipPath
Write-Host "Archive: $($zipItem.FullName) ($([math]::Round($zipItem.Length / 1MB, 2)) MB)"

# Clean staging folder
Remove-Item -Recurse -Force "staging_zip"
