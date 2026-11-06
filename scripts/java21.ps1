function Resolve-Java21 {
    param([string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot')
    $javaPath = Join-Path $Jdk 'bin/java.exe'
    if (!(Test-Path -LiteralPath $javaPath)) { throw 'JDK 21 não encontrado. Informe -Jdk com o diretório correto.' }
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $javaPath
    $info.Arguments = '-version'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    try {
        $version = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or $version -notmatch 'version "21(?:\.|"|-)') { throw 'A execução exige Java 21; outro Java não será utilizado.' }
    } finally { $process.Dispose() }
    return [System.IO.Path]::GetFullPath($Jdk)
}
