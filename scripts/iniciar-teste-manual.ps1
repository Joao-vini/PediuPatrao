param(
    [string]$MongoExecutable = 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe',
    [switch]$VerificarSemJanela
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/java21.ps1"
$manualJdk = Resolve-Java21
$manualRepo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $manualRepo
if (!(Test-Path -LiteralPath $MongoExecutable)) { throw 'MongoDB não encontrado. Informe -MongoExecutable com o caminho do mongod.exe instalado.' }
if ((git branch --show-current).Trim() -ne 'feature/dashboard-administrativo') { throw 'Use a branch feature/dashboard-administrativo.' }
if (Get-NetTCPConnection -LocalPort 27028,18088,9228 -State Listen -ErrorAction SilentlyContinue) { throw 'Uma porta de teste está ocupada. Não reutilize servidores existentes; encerre somente seus testes anteriores.' }
$manualId = [guid]::NewGuid().ToString('N')
$manualTarget = [System.IO.Path]::GetFullPath((Join-Path $manualRepo 'target'))
$manualData = [System.IO.Path]::GetFullPath((Join-Path $manualTarget "mongo-manual-$manualId"))
if (!$manualData.StartsWith($manualTarget + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Diretório de teste fora de target.' }
New-Item -ItemType Directory -Path $manualData | Out-Null
$manualMongo = $null
$manualEnv = @{}
foreach ($name in @('PP_TEST_MONGO_URI','PP_TEST_MONGO_INIT','JAVA_HOME','Path')) { $manualEnv[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:JAVA_HOME = $manualJdk
    $env:Path = (Join-Path $manualJdk 'bin') + ';' + $env:Path
    $manualMongo = Start-Process -FilePath $MongoExecutable -ArgumentList @('--dbpath',"`"$manualData`"",'--port','27028','--bind_ip','127.0.0.1','--replSet','codexDashboard','--logpath',"`"$manualData\mongod.log`"",'--wiredTigerCacheSizeGB','0.25') -WindowStyle Hidden -PassThru
    $env:PP_TEST_MONGO_URI = 'mongodb://127.0.0.1:27028/pediupatrao_test_manual?directConnection=true&serverSelectionTimeoutMS=10000'
    $env:PP_TEST_MONGO_INIT = 'codexDashboard'
    Write-Host 'Preparando replica set novo e JAR. Nenhum banco existente será reutilizado.'
    $manualPreference = $ErrorActionPreference
    try {
        # PowerShell 5 trata avisos nativos em stderr como erro. O código de saída
        # do Maven é a autoridade para sucesso/falha, não o canal da mensagem.
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd -o "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" '-Dtest=PedidoMongoIntegrationTest' package > (Join-Path $manualTarget "manual-preparacao-$manualId.log") 2>&1
        $manualBuildExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $manualPreference }
    if ($manualBuildExit -ne 0) { throw "Preparação falhou. Consulte target/manual-preparacao-$manualId.log. Não inicie o banco original." }
    Write-Host 'Abrindo demonstração isolada em http://127.0.0.1:18088/dashboard. Siga docs/testes.md.'
    if ($VerificarSemJanela) { & "$PSScriptRoot/verificar-dashboard-local.ps1" -Demonstracao }
    else { & "$PSScriptRoot/verificar-dashboard-local.ps1" -Apresentacao }
} finally {
    # Somente o processo criado nesta execução; não apaga o diretório de dados.
    if ($manualMongo -and !$manualMongo.HasExited) { Stop-Process -Id $manualMongo.Id }
    foreach ($name in $manualEnv.Keys) { [Environment]::SetEnvironmentVariable($name,$manualEnv[$name],'Process') }
}
