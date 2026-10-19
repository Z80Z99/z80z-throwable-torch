# Headless runServer smoke test for the throwable-torches mod.
#
# Verifies the server-side pipeline without a real player connection:
#   1. mod loads and the dedicated server reaches "Done"
#   2. the thrown_torch entity is registered (summon succeeds)
#   3. a summoned torch flies, lands and converts to a vanilla torch item drop
#   4. no leftover projectile entity and no fatal errors in the log
#
# Placement-on-landing needs a ServerPlayer owner and is not covered here;
# that part is validated by manual client testing (README acceptance list).
[CmdletBinding()]
param(
    [int] $TimeoutSec = 600,
    [int] $RconPort = 25576,
    [string] $RconPassword = 'tt-qa',
    [string] $SpawnY = '-60'
)

$ErrorActionPreference = 'Stop'
$modRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'qa-lib.ps1')

$evidenceDir = Join-Path $modRoot 'logs'
if (-not (Test-Path $evidenceDir)) { New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null }
$evidenceLog = Join-Path $evidenceDir 'qa-server-smoke.log'

$cfg = Ensure-ServerConfig -ModRoot $modRoot -RconPassword $RconPassword -RconPort $RconPort
$proc = Start-TestServer -ModRoot $modRoot
$conn = $null
try {
    Wait-ForLog -LogPath $cfg.LogPath -Pattern 'Done \(' -TimeoutSec $TimeoutSec -Phase 'server ready' -Proc $proc

    $conn = Connect-Rcon -Port $RconPort -Password $RconPassword -TimeoutSec 120

    # Mod presence: a failed mod load aborts startup, so "Done" already proves it;
    # the entity summon below proves registration on top of that.
    $summon = Invoke-Rcon -Conn $conn -Command "summon mod_a3c26b8e:thrown_torch 0.5 $SpawnY.0 0.5 {Motion:[1.5f,0.6f,0.0f],Recoverable:1b}"
    Write-Host "[qa] summon response: $summon"

    $null = Invoke-Rcon -Conn $conn -Command 'execute if entity @e[type=mod_a3c26b8e:thrown_torch,limit=1] run say TT-SPAWNED'
    Wait-ForLog -LogPath $cfg.LogPath -Pattern 'TT-SPAWNED' -TimeoutSec 30 -Phase 'TT-SPAWNED' -Proc $proc

    Start-Sleep -Seconds 6
    $null = Invoke-Rcon -Conn $conn -Command 'execute if entity @e[type=minecraft:item,limit=1] run say TT-ITEM-DROPPED'
    Wait-ForLog -LogPath $cfg.LogPath -Pattern 'TT-ITEM-DROPPED' -TimeoutSec 30 -Phase 'TT-ITEM-DROPPED' -Proc $proc

    $null = Invoke-Rcon -Conn $conn -Command 'execute if entity @e[type=minecraft:item,nbt={Item:{id:"minecraft:torch"}}] run say TT-TORCH-ITEM'
    Wait-ForLog -LogPath $cfg.LogPath -Pattern 'TT-TORCH-ITEM' -TimeoutSec 30 -Phase 'TT-TORCH-ITEM' -Proc $proc

    # Projectile must be fully discarded a few seconds after landing.
    $null = Invoke-Rcon -Conn $conn -Command 'execute if entity @e[type=mod_a3c26b8e:thrown_torch] run say TT-STILL-FLYING'
    Start-Sleep -Seconds 3
} finally {
    if ($null -ne $conn) { try { Invoke-Rcon -Conn $conn -Command 'stop' | Out-Null } catch {} ; try { $conn.Client.Close() } catch {} }
    try { if (-not $proc.WaitForExit(90000)) { $proc.Kill() } } catch {}
}

$text = Get-LogText -LogPath $cfg.LogPath
$failed = @()
foreach ($r in @('Done (', 'TT-SPAWNED', 'TT-ITEM-DROPPED', 'TT-TORCH-ITEM')) {
    if (-not ($text -match [regex]::Escape($r))) { $failed += $r }
}
if ($text -match 'TT-STILL-FLYING') { $failed += 'TT-STILL-FLYING (projectile not discarded)' }
if ($text -match 'LoadingFailedException|Failed to load mod|Fatal') { $failed += 'mod load error in log' }

$exceptionCount = ([regex]::Matches($text, 'Exception')).Count
$tail = if (Test-Path $cfg.LogPath) { Get-Content -LiteralPath $cfg.LogPath -Tail 200 } else { @() }
Set-Content -LiteralPath $evidenceLog -Value $tail -Encoding UTF8

if ($failed.Count -gt 0) {
    Write-Host "[qa] FAIL: $($failed -join ', ') (exceptions in log: $exceptionCount)"
    exit 1
}
Write-Host "[qa] PASS: server ready, entity registered, flight->landing->torch item drop, projectile discarded (exceptions in log: $exceptionCount)"
exit 0
