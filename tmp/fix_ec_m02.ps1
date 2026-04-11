$file = Join-Path $env:USERPROFILE '.gemini\antigravity\scratch\iptv-player\data\src\main\java\com\streamvault\data\sync\SyncManager.kt'
$lines = [System.IO.File]::ReadAllLines($file)
$idx = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match 'suspend fun onProviderDeleted') { $idx = $i; break }
}
if ($idx -eq -1) { Write-Error "Pattern not found"; exit 1 }

# Find the line with backgroundEpgJobs.remove(providerId)?.cancel()
$cancelIdx = -1
for ($i = $idx; $i -lt [Math]::Min($idx + 10, $lines.Length); $i++) {
    if ($lines[$i] -match 'backgroundEpgJobs\.remove\(providerId\)\?\.cancel\(\)') { $cancelIdx = $i; break }
}
if ($cancelIdx -eq -1) { Write-Error "Cancel line not found"; exit 1 }

Write-Output "Found onProviderDeleted at line $($idx + 1), cancel at line $($cancelIdx + 1)"
Write-Output "Cancel line content: $($lines[$cancelIdx])"

# Replace the plain cancel() with a version that logs if job was active
$newLines = [System.Collections.Generic.List[string]]::new($lines)
$originalLine = $newLines[$cancelIdx]
$indent = '        ' # 8 spaces
$newLines[$cancelIdx] = "$indent" + 'val cancelledEpgJob = backgroundEpgJobs.remove(providerId)'
$newLines.Insert($cancelIdx + 1, "$indent" + 'if (cancelledEpgJob?.isActive == true) {')
$newLines.Insert($cancelIdx + 2, "$indent" + '    Log.w(TAG, "onProviderDeleted($providerId): cancelled an active background EPG job — mid-sync delete detected")')
$newLines.Insert($cancelIdx + 3, "$indent" + '}')
$newLines.Insert($cancelIdx + 4, "$indent" + 'cancelledEpgJob?.cancel()')

[System.IO.File]::WriteAllLines($file, $newLines.ToArray(), [System.Text.UTF8Encoding]::new($false))
Write-Output "Done."
