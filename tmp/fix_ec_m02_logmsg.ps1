$file = Join-Path $env:USERPROFILE '.gemini\antigravity\scratch\iptv-player\data\src\main\java\com\streamvault\data\sync\SyncManager.kt'
$lines = [System.IO.File]::ReadAllLines($file)
$idx = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match 'cancelled an active background EPG job') { $idx = $i; break }
}
if ($idx -eq -1) { Write-Error "Line not found"; exit 1 }
Write-Output "Fixing line $($idx + 1): $($lines[$idx])"
$lines[$idx] = '            Log.w(TAG, "onProviderDeleted($providerId): cancelled an active background EPG job; mid-sync delete detected")'
[System.IO.File]::WriteAllLines($file, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Done."
