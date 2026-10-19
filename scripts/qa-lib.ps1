# Shared helpers for the headless runServer QA harness.
# The QA commands are driven over RCON because Gradle's runServer task does not
# forward this process's stdin to the forked server JVM.
$ErrorActionPreference = 'Stop'

function Ensure-ServerConfig {
    param(
        [Parameter(Mandatory = $true)][string] $ModRoot,
        [string] $RconPassword = 've-qa',
        [int] $RconPort = 25575,
        [string] $LevelName = 'qa_world',
        [switch] $KeepWorld
    )
    $runDir = Join-Path $ModRoot 'run'
    if (-not (Test-Path $runDir)) { New-Item -ItemType Directory -Path $runDir -Force | Out-Null }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ASCII

    # Fresh world each run keeps QA reproducible; -KeepWorld is used for
    # restart/persistence checks.
    if (-not $KeepWorld) {
        $levelDir = Join-Path $runDir $LevelName
        if (Test-Path $levelDir) { Remove-Item -LiteralPath $levelDir -Recurse -Force -ErrorAction SilentlyContinue }
    }

    $sp = Join-Path $runDir 'server.properties'
    $settings = [ordered]@{
        'enable-rcon'           = 'true'
        'rcon.password'         = $RconPassword
        'rcon.port'             = "$RconPort"
        'broadcast-rcon-to-ops' = 'true'
        'level-type'            = 'minecraft:flat'
        'level-name'            = $LevelName
        'online-mode'           = 'false'
        'spawn-protection'      = '0'
        'difficulty'            = 'normal'
        'gamemode'              = 'creative'
        'max-tick-time'         = '-1'
    }
    $lines = @()
    if (Test-Path $sp) { $lines = @(Get-Content -LiteralPath $sp) }
    foreach ($key in $settings.Keys) {
        $found = $false
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match ('^\s*' + [regex]::Escape($key) + '\s*=')) { $lines[$i] = "$key=$($settings[$key])"; $found = $true; break }
        }
        if (-not $found) { $lines += "$key=$($settings[$key])" }
    }
    Set-Content -LiteralPath $sp -Value $lines -Encoding ASCII
    return [pscustomobject]@{ RunDir = $runDir; LogPath = (Join-Path $runDir "logs\latest.log") }
}

function Start-TestServer {
    param([Parameter(Mandatory = $true)][string] $ModRoot)
    if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
    $sock = Join-Path ([IO.Path]::GetTempPath()) ('ve-socket-' + [guid]::NewGuid().ToString('N'))
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' "-Djdk.net.unixdomain.tmpdir=' + $sock + '"').Trim()

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:ComSpec
    $psi.Arguments = '/c ""' + (Join-Path $ModRoot 'gradlew.bat') + '" runServer --no-daemon --console=plain"'
    $psi.WorkingDirectory = $ModRoot
    $psi.UseShellExecute = $false
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $null = $proc.Start()
    return $proc
}

function Get-LogText {
    param([Parameter(Mandatory = $true)][string] $LogPath)
    if (Test-Path $LogPath) { return [string](Get-Content -LiteralPath $LogPath -Raw -ErrorAction SilentlyContinue) }
    return ''
}

function Wait-ForLog {
    param(
        [Parameter(Mandatory = $true)][string] $LogPath,
        [Parameter(Mandatory = $true)][string] $Pattern,
        [int] $TimeoutSec = 60,
        [string] $Phase = 'log',
        $Proc = $null
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Proc -and $Proc.HasExited) { throw "server exited early during '$Phase' (exit=$($Proc.ExitCode))" }
        $text = Get-LogText -LogPath $LogPath
        if ($text -and ($text -match $Pattern)) { Write-Host "[qa] saw $Phase"; return }
        Start-Sleep -Seconds 2
    }
    throw "timeout after ${TimeoutSec}s waiting for $Phase ($Pattern)"
}

function Wait-ForLogCount {
    param(
        [Parameter(Mandatory = $true)][string] $LogPath,
        [Parameter(Mandatory = $true)][string] $Pattern,
        [int] $MinCount = 1,
        [int] $TimeoutSec = 60,
        [string] $Phase = 'log',
        $Proc = $null
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Proc -and $Proc.HasExited) { throw "server exited early during '$Phase' (exit=$($Proc.ExitCode))" }
        $text = Get-LogText -LogPath $LogPath
        $count = ([regex]::Matches($text, $Pattern)).Count
        if ($count -ge $MinCount) { Write-Host "[qa] saw $Phase x$count"; return }
        Start-Sleep -Seconds 2
    }
    throw "timeout after ${TimeoutSec}s waiting for $Phase (>=${MinCount}x $Pattern)"
}

function Send-RconPacket {
    param($Conn, [int] $Type, [string] $Payload)
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
    $len = 4 + 4 + $payloadBytes.Length + 2
    $buf = New-Object byte[] (4 + $len)
    [BitConverter]::GetBytes([int] $len).CopyTo($buf, 0)
    [BitConverter]::GetBytes([int] $Conn.Id).CopyTo($buf, 4)
    [BitConverter]::GetBytes([int] $Type).CopyTo($buf, 8)
    $payloadBytes.CopyTo($buf, 12)
    $Conn.Stream.Write($buf, 0, $buf.Length)
    $Conn.Stream.Flush()
}

function Read-RconPacket {
    param($Conn)
    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $Conn.Stream.Read($lenBuf, $read, 4 - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    if ($len -lt 10) { return $null }
    $data = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $Conn.Stream.Read($data, $read, $len - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    return [pscustomobject]@{
        Id   = [BitConverter]::ToInt32($data, 0)
        Type = [BitConverter]::ToInt32($data, 4)
        Body = [System.Text.Encoding]::UTF8.GetString($data, 8, $len - 10)
    }
}

function Connect-Rcon {
    param([string] $Server = '127.0.0.1', [int] $Port = 25575, [string] $Password = 've-qa', [int] $TimeoutSec = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $client = $null
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $client.Connect($Server, $Port)
            $stream = $client.GetStream()
            $stream.ReadTimeout = 20000
            $stream.WriteTimeout = 20000
            $conn = [pscustomobject]@{ Client = $client; Stream = $stream; Id = 1 }
            Send-RconPacket -Conn $conn -Type 3 -Payload $Password
            $auth = $null
            for ($i = 0; $i -lt 5; $i++) {
                $p = Read-RconPacket -Conn $conn
                if ($null -eq $p) { break }
                if ($p.Type -eq 2) { $auth = $p; break }
            }
            if ($null -ne $auth -and $auth.Id -ne -1) { return $conn }
            $client.Close()
        } catch {
            if ($null -ne $client) { try { $client.Close() } catch {} }
        }
        Start-Sleep -Seconds 2
    }
    throw "RCON connect timeout after ${TimeoutSec}s"
}

function Invoke-Rcon {
    param($Conn, [string] $Command)
    $Conn.Id = $Conn.Id + 1
    Send-RconPacket -Conn $Conn -Type 2 -Payload $Command
    try {
        $p = Read-RconPacket -Conn $Conn
        if ($null -eq $p) { return '' }
        return $p.Body
    } catch {
        return ''
    }
}
