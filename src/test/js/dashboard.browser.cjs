// Teste real de navegador e aplicação contra banco local vazio e descartável.
// Credenciais aleatórias entram somente pelo ambiente do processo lançador.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {randomUUID} = require('node:crypto');
const base = 'http://127.0.0.1:18088';
let ws;
const pending = new Map(); let sequence = 0;
const browserErrors=[];
let aceitarConfirmacao=false;
const pausa = ms => new Promise(resolve => setTimeout(resolve, ms));
async function command(method, params = {}) {
    return new Promise((resolve, reject) => {
        const id = ++sequence; pending.set(id, {resolve,reject});
        ws.send(JSON.stringify({id, method, params}));
    });
}
async function evaluate(expression) {
    const r = await command('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true});
    if (r.exceptionDetails) throw new Error('Falha em execução no navegador (sem expor parâmetros).');
    return r.result.value;
}
async function esperar(expression) {
    for (let i=0; i<100; i++) { if (await evaluate(expression)) return; await pausa(100); }
    throw new Error('Tempo limite de carregamento da página.');
}
async function navegar(path, destino = path) {
    await command('Page.navigate', {url:base + path});
    await esperar('document.readyState === "complete" && location.pathname === ' + JSON.stringify(destino.split('?')[0]));
}
async function login(username, password) {
    await navegar('/login'); await esperar('!!document.querySelector("#username")');
    await evaluate(`document.querySelector('#username').value=${JSON.stringify(username)};document.querySelector('#password').value=${JSON.stringify(password)};document.querySelector('form').submit();`);
    await esperar('location.pathname !== "/login" && document.readyState === "complete"');
}
async function request(path, options) {
    return evaluate(`(async()=>{const r=await fetch(${JSON.stringify(path)},${JSON.stringify(options || {})});return {status:r.status,redirected:r.redirected,body:await r.text()};})()`);
}
async function main() {
    assert.ok(process.env.PP_BROWSER_USER && process.env.PP_BROWSER_PASSWORD, 'Use o lançador PowerShell.');
    const tab = await (await fetch('http://127.0.0.1:9228/json/new?about:blank', {method:'PUT'})).json();
    ws = new WebSocket(tab.webSocketDebuggerUrl);
    await new Promise((resolve,reject) => { ws.onopen=resolve;ws.onerror=reject; });
    ws.onmessage = event => {
        const r=JSON.parse(event.data);
        if(r.method==='Page.javascriptDialogOpening' && aceitarConfirmacao) {
            aceitarConfirmacao=false;command('Page.handleJavaScriptDialog',{accept:true}).catch(()=>{});
        }
        if(r.method==='Runtime.exceptionThrown') browserErrors.push('Exceção JavaScript não tratada');
        if(r.method==='Runtime.consoleAPICalled' && r.params.type==='error') browserErrors.push('console.error na aplicação');
        if (!r.id) return;
        const p=pending.get(r.id); if (!p) return; pending.delete(r.id);
        if (r.error) p.reject(new Error('Comando CDP falhou: ' + r.error.message)); else p.resolve(r.result);
    };
    await command('Page.enable');
    await command('Runtime.enable');
    await command('Emulation.setDeviceMetricsOverride', {width:1440,height:1100,deviceScaleFactor:1,mobile:false});
    await navegar('/dashboard', '/login'); await esperar('location.pathname === "/login"');
    console.log('OK: dashboard anônimo redireciona para login.');
    await login(process.env.PP_BROWSER_USER, process.env.PP_BROWSER_PASSWORD);
    await navegar('/dashboard');
    let demonstracao;
    if(process.env.PP_BROWSER_DEMO_EXPECTED) {
        demonstracao=await require('./dashboard.demonstracao.cjs')({evaluate,command,esperar,request,pausa});
    }
    await esperar('document.querySelector("#dashboard-mensagem")?.textContent.includes("Nenhum pedido encontrado")');
    assert.equal(await evaluate('typeof Chart'), 'function', 'Chart.js não carregou');
    assert.equal(await evaluate('typeof jQuery.fn.PushMenu'), 'function', 'Plugin PushMenu não carregou');
    assert.equal(await evaluate('document.querySelectorAll("canvas").length'), 3);
    assert.equal(await evaluate('document.querySelector("#dashboard-resultados").hidden'), false);
    assert.ok(await evaluate('document.querySelector(".main-header").textContent.includes(' + JSON.stringify(process.env.PP_BROWSER_USER) + ')'));
    for (const suffix of ['', '/indicadores','/vendas','/status','/produtos']) {
        const r=await request('/api/admin/dashboard'+suffix); assert.equal(r.status,200); JSON.parse(r.body);
    }
    console.log('OK: login ADMIN, AdminLTE/Chart.js locais e cinco endpoints reais com base vazia.');
    await evaluate('document.querySelector("#dashboard-filtros").elements.tipo.value="RETIRADA";document.querySelector("#dashboard-filtros").requestSubmit()');
    await esperar('!document.querySelector("#dashboard-filtros button").disabled');
    assert.ok(await evaluate('document.querySelector("#dashboard-mensagem").textContent.includes("Nenhum pedido")'));
    const erro=await request('/api/admin/dashboard?inicio=invalido'); assert.equal(erro.status,400); assert.ok(JSON.parse(erro.body).erro);
    await evaluate('void jQuery("[data-widget=pushmenu]").PushMenu("expand")');
    await command('Input.dispatchMouseEvent', {type:'mouseMoved',x:1000,y:200});
    await pausa(600);
    console.log('Layout desktop: ' + JSON.stringify(await evaluate('({largura:innerWidth,conteudo:document.querySelector(".content-wrapper").getBoundingClientRect().left,menu:document.querySelector(".main-sidebar").getBoundingClientRect().right,classes:document.body.className})')));
    assert.ok(await evaluate('document.querySelector(".content-wrapper").getBoundingClientRect().left >= document.querySelector(".main-sidebar").getBoundingClientRect().right - 1'), 'Sidebar sobrepõe conteúdo no desktop');
    assert.equal(await evaluate('Object.keys(Chart.instances).length'), 3);
    const shot=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});
    fs.writeFileSync('target/adminlte-desktop.png',Buffer.from(shot.data,'base64'));
    await command('Emulation.setDeviceMetricsOverride', {width:390,height:844,deviceScaleFactor:1,mobile:true});
    await pausa(400);
    assert.ok(await evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1'), 'Overflow horizontal em viewport móvel');
    const mobile=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
    fs.writeFileSync('target/adminlte-mobile.png',Buffer.from(mobile.data,'base64'));
    await evaluate('void jQuery("[data-widget=pushmenu]").PushMenu("collapse")'); await pausa(400);
    await evaluate('document.querySelector("[data-widget=pushmenu]").click()');
    await esperar('document.body.classList.contains("sidebar-open")');
    await evaluate('document.querySelector("[data-widget=pushmenu]").click()');
    await esperar('!document.body.classList.contains("sidebar-open")');
    console.log('OK: filtros, JSON 400, três gráficos, capturas desktop/móvel e ausência de overflow horizontal.');
    await command('Network.enable');
    await command('Network.setBlockedURLs', {urls:['*api/admin/dashboard*']});
    await evaluate('document.querySelector("#dashboard-filtros").requestSubmit()');
    await esperar('document.querySelector("#dashboard-mensagem").classList.contains("dash-error")');
    assert.equal(await evaluate('document.querySelector("#dashboard-resultados").hidden'), true);
    await command('Network.setBlockedURLs', {urls:[]});
    await evaluate('document.querySelector("#dashboard-filtros").requestSubmit()');
    await esperar('document.querySelector("#dashboard-mensagem").textContent.includes("Nenhum pedido")');
    console.log('OK: menu móvel, falha de rede exibida e recuperação por nova atualização.');
    const csrf=await evaluate('document.querySelector("input[name=_csrf]").value');
    const profiles=[];
    for(const role of ['GERENTE','ATENDENTE']) {
        const username='teste-'+role.toLowerCase()+'-'+randomUUID().slice(0,8), password=randomUUID()+randomUUID();
        const created=await request('/api/usuarios',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':csrf},body:JSON.stringify({username,password,role})});
        assert.equal(created.status,201); profiles.push({username,password});
    }
    for (const p of profiles) {
        const token=await evaluate('document.querySelector("input[name=_csrf]").value');
        await request('/logout',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:token}).toString()});
        await login(p.username,p.password);
        for(const path of ['/dashboard','/api/admin/dashboard','/api/admin/dashboard/indicadores','/api/admin/dashboard/vendas','/api/admin/dashboard/status','/api/admin/dashboard/produtos']) {
            const r=await request(path); assert.equal(r.status,403);
        }
    }
    console.log('OK: GERENTE e ATENDENTE bloqueados na página e nos cinco endpoints.');
    if(process.env.PP_BROWSER_DEMO_EXPECTED) {
        await require('./dashboard.descontos.browser.cjs')({evaluate,request,login,navegar,esperar,
            confirmar:()=>{aceitarConfirmacao=true;},gerente:profiles[0]});
    }
    const tokenFinal=await evaluate('document.querySelector("input[name=_csrf]").value');
    await request('/logout',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:tokenFinal}).toString()});
    await login(process.env.PP_BROWSER_USER,process.env.PP_BROWSER_PASSWORD); await navegar('/dashboard');
    await esperar('!document.querySelector("#dashboard-filtros button").disabled');
    const semCsrf=await request('/api/usuarios',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}); assert.equal(semCsrf.status,403);
    const tokenAdmin=await evaluate('document.querySelector("input[name=_csrf]").value');
    await request('/logout',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:tokenAdmin}).toString()});
    await evaluate('document.querySelector("#dashboard-filtros").requestSubmit()');
    await esperar('document.querySelector("#dashboard-mensagem").textContent.includes("Sessão encerrada")');
    assert.equal(await evaluate('document.querySelector("#dashboard-resultados").hidden'),true);
    console.log('OK: CSRF obrigatório e expiração real da sessão sem fallback de indicadores.');
    assert.deepEqual(browserErrors,[],'Erros JavaScript no console');
    if(process.env.PP_BROWSER_RETURN_ADMIN==='true') {
        await login(process.env.PP_BROWSER_USER,process.env.PP_BROWSER_PASSWORD); await navegar('/dashboard');
        await esperar('!document.querySelector("#dashboard-filtros button").disabled');
        await demonstracao.identificar(); await demonstracao.filtrar(demonstracao.padrao);
        await command('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
    }
}
main().then(()=>{ws?.close();console.log('VALIDAÇÃO DE NAVEGADOR CONCLUÍDA — ambiente descartável; cenário vazio e, quando habilitado, dados sintéticos identificados.');})
    .catch(error=>{ws?.close();console.log('FALHA: ' + error.message);process.exitCode=1;});
