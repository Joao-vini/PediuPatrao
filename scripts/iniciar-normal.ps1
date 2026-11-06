param(
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot',
    [switch]$VerificarAmbiente
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/java21.ps1"
$normalJdk = Resolve-Java21 -Jdk $Jdk
$normalRepo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $normalRepo
if ((git branch --show-current).Trim() -ne 'feature/dashboard-administrativo') { throw 'Use a branch feature/dashboard-administrativo.' }
if ($VerificarAmbiente) {
    Write-Host 'Java 21 confirmado. Execução normal usa application.properties / SPRING_DATA_MONGODB_URI, sem fixtures ou apresentação.'
    Write-Host 'O banco será verificado por hello ao iniciar: escritas auditadas exigem replica set ou cluster compatível.'
    return
}
$normalEnv = @{}
foreach ($name in @('JAVA_HOME','Path','PP_TEST_MONGO_URI','PP_TEST_MONGO_INIT','PP_DEMO_URI','PP_DEMO_EXPECTED','PP_BROWSER_DEMO_EXPECTED','PP_BROWSER_RETURN_ADMIN')) {
    $normalEnv[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
}
try {
    $env:JAVA_HOME = $normalJdk
    $env:Path = (Join-Path $normalJdk 'bin') + ';' + $env:Path
    foreach ($name in @('PP_TEST_MONGO_URI','PP_TEST_MONGO_INIT','PP_DEMO_URI','PP_DEMO_EXPECTED','PP_BROWSER_DEMO_EXPECTED','PP_BROWSER_RETURN_ADMIN')) {
        [Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    Write-Host 'Iniciando Pediu Patrão normalmente com Java 21. Nenhum dado de demonstração será criado.'
    $ErrorActionPreference = 'Continue'
    & .\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
    $normalExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($normalExit -ne 0) { throw 'A aplicação não iniciou ou foi encerrada com erro. Confira a disponibilidade/topologia do MongoDB e a configuração externa.' }
} finally {
    foreach ($name in $normalEnv.Keys) { [Environment]::SetEnvironmentVariable($name,$normalEnv[$name],'Process') }
}
