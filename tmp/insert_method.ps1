$file = 'c:\Users\david\.gemini\antigravity\scratch\iptv-player\data\src\main\java\com\streamvault\data\sync\SyncManager.kt'
$lines = [System.IO.File]::ReadAllLines($file)
$idx = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match 'syncStateTracker\.current\(providerId\)') { $idx = $i; break }
}
if ($idx -eq -1) { Write-Error "Pattern not found"; exit 1 }
Write-Output "Inserting after 0-based line $idx"
$newLines = [System.Collections.Generic.List[string]]::new($lines)
$newLines.Insert($idx + 1, '')
$newLines.Insert($idx + 2, '    /** Returns true if any provider sync mutex is currently held (used by DatabaseMaintenanceManager). */')
$newLines.Insert($idx + 3, '    fun isAnySyncActive(): Boolean = providerSyncMutexes.values.any { it.isLocked }')
[System.IO.File]::WriteAllLines($file, $newLines.ToArray(), [System.Text.UTF8Encoding]::new($false))
Write-Output "Done."
