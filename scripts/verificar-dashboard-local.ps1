param([string]$Chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe', [switch]$Demonstracao, [switch]$Apresentacao)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/java21.ps1"
$dashboardJdk = Resolve-Java21
$repoDashboard = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $repoDashboard
if (!(Test-Path -LiteralPath $Chrome)) { throw 'Informe o executável Chrome com -Chrome.' }
if (!(Test-Path -LiteralPath 'target/pediupatrao-1.0-SNAPSHOT.jar')) { throw 'Execute o build Maven primeiro.' }
if (Get-NetTCPConnection -LocalPort 18088,9228 -State Listen -ErrorAction SilentlyContinue) { throw 'Portas de teste ocupadas; não reutilizar processos existentes.' }
# Exige o replica set DESCARTÁVEL dedicado já iniciado. Nunca lê URI de produção.
$testId = [guid]::NewGuid().ToString('N')
if ($Apresentacao) { $Demonstracao = $true }
$previousDashboardEnvironment = @{}
foreach ($name in @('SPRING_DATA_MONGODB_URI','SERVER_PORT','SERVER_ADDRESS','APP_BOOTSTRAP_ADMIN_ENABLED','APP_BOOTSTRAP_ADMIN_USERNAME','APP_BOOTSTRAP_ADMIN_PASSWORD','PP_BROWSER_USER','PP_BROWSER_PASSWORD','PP_DEMO_URI','PP_DEMO_EXPECTED','PP_BROWSER_DEMO_EXPECTED','PP_BROWSER_RETURN_ADMIN','JAVA_HOME','Path')) {
    $previousDashboardEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$env:SPRING_DATA_MONGODB_URI = "mongodb://127.0.0.1:27028/pediupatrao_test_browser_$testId`?replicaSet=codexDashboard&serverSelectionTimeoutMS=3000"
$env:JAVA_HOME = $dashboardJdk
$env:Path = (Join-Path $dashboardJdk 'bin') + ';' + $env:Path
if ($Demonstracao) { $env:SPRING_DATA_MONGODB_URI = "mongodb://127.0.0.1:27028/pediupatrao_test_demo_$testId`?replicaSet=codexDashboard&serverSelectionTimeoutMS=3000" }
$env:SERVER_PORT = '18088'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:APP_BOOTSTRAP_ADMIN_USERNAME = 'teste-admin-' + $testId.Substring(0,8)
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:PP_BROWSER_USER = $env:APP_BOOTSTRAP_ADMIN_USERNAME
$env:PP_BROWSER_PASSWORD = $env:APP_BOOTSTRAP_ADMIN_PASSWORD
$appDashboard = $null
$browserDashboard = $null
try {
    if ($Demonstracao) {
        $env:PP_DEMO_URI = $env:SPRING_DATA_MONGODB_URI
        $env:PP_DEMO_EXPECTED = Join-Path $repoDashboard "target/homologacao-esperado-$testId.json"
        $fixturePreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\mvnw.cmd -o "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" '-Dtest=DashboardDemonstracaoTest' test > "target/homologacao-fixture-$testId.log" 2>&1
            $fixtureExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = $fixturePreference }
        if ($fixtureExit -ne 0) { throw 'Fixture recusada ou com falha; consulte target/homologacao-fixture-*.log. A aplicação não será iniciada.' }
        $env:PP_BROWSER_DEMO_EXPECTED = $env:PP_DEMO_EXPECTED
        Copy-Item -LiteralPath $env:PP_DEMO_EXPECTED -Destination 'target/homologacao-esperado-ultima.json'
    }
    if ($Apresentacao) { $env:PP_BROWSER_RETURN_ADMIN = 'true' }
    $appDashboard = Start-Process -FilePath 'java' -ArgumentList @('-jar','target/pediupatrao-1.0-SNAPSHOT.jar') -WorkingDirectory $repoDashboard -WindowStyle Hidden -PassThru -RedirectStandardOutput 'target/adminlte-app.log' -RedirectStandardError 'target/adminlte-app-error.log'
    $ready = $false
    for ($i=0; $i -lt 120; $i++) {
        if ($appDashboard.HasExited) { throw 'A aplicação encerrou. Consulte target/adminlte-app-error.log.' }
        try { $response = Invoke-WebRequest 'http://127.0.0.1:18088/login' -UseBasicParsing; if ($response.StatusCode -eq 200) { $ready=$true; break } } catch { }
        Start-Sleep -Milliseconds 250
    }
    if (!$ready) { throw 'Aplicação não ficou pronta no tempo previsto.' }
    $profileDashboard = Join-Path $repoDashboard "target/chrome-adminlte-$testId"
    $chromeArgs = @('--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-address=127.0.0.1','--remote-debugging-port=9228',"--user-data-dir=$profileDashboard",'about:blank')
    $chromeWindow = 'Hidden'
    if (!$Apresentacao) { $chromeArgs = @('--headless=new') + $chromeArgs } else { $chromeWindow = 'Normal' }
    $browserDashboard = Start-Process -FilePath $Chrome -ArgumentList $chromeArgs -WindowStyle $chromeWindow -PassThru
    $browserReady=$false
    for ($i=0; $i -lt 60; $i++) {
        try { Invoke-WebRequest 'http://127.0.0.1:9228/json/version' -UseBasicParsing | Out-Null; $browserReady=$true; break } catch { Start-Sleep -Milliseconds 250 }
    }
    if (!$browserReady) { throw 'Chrome não abriu o endpoint CDP.' }
    & node src/test/js/dashboard.browser.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Validação do navegador falhou.' }
    if ($Apresentacao) { Read-Host 'Demonstração sintética pronta no Chrome. Grave o vídeo manualmente. Pressione Enter para encerrar os processos de teste' | Out-Null }
} finally {
    if ($browserDashboard -and !$browserDashboard.HasExited) { Stop-Process -Id $browserDashboard.Id }
    if ($appDashboard -and !$appDashboard.HasExited) { Stop-Process -Id $appDashboard.Id }
    foreach ($name in $previousDashboardEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $previousDashboardEnvironment[$name], 'Process') }
}
