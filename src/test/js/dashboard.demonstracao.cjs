// Complemento opt-in: preserva todos os cenários vazios de dashboard.browser.cjs.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const moeda = n => Number(n).toLocaleString('pt-BR',{style:'currency',currency:'BRL'}).replace(/\s/g,' ');
const normal = s => String(s).replace(/\s/g,' ');

module.exports = async function homologar({evaluate,command,esperar,request,pausa}) {
    const esperado=JSON.parse(fs.readFileSync(process.env.PP_BROWSER_DEMO_EXPECTED,'utf8'));
    const aviso=esperado.aviso;
    async function identificar() {
        await evaluate(`(()=>{if(!document.getElementById('aviso-demonstracao')){const aviso=document.createElement('div');aviso.id='aviso-demonstracao';aviso.setAttribute('role','note');aviso.textContent=${JSON.stringify(aviso)};aviso.style.cssText='background:#fff1bc;color:#543d00;padding:14px;font-weight:bold;border:2px solid #b57c00;margin:12px 0';document.querySelector('.dashboard').prepend(aviso);}})()`);
    }
    async function filtrar(parametros) {
        await evaluate(`(()=>{const f=document.querySelector('#dashboard-filtros');Object.entries(${JSON.stringify(parametros)}).forEach(([k,v])=>f.elements[k].value=v);f.requestSubmit();})()`);
        await esperar('!document.querySelector("#dashboard-filtros button").disabled');
        assert.equal(await evaluate('document.querySelector("#dashboard-resultados").hidden'),false);
    }
    const padrao={inicio:esperado.inicio,fim:esperado.fim,status:'',tipo:''};
    await esperar('!document.querySelector("#dashboard-filtros button").disabled');
    await identificar(); await evaluate('window.__homologacaoSemReload="preservado"');
    for(const caso of esperado.casos) {
        const query=new URLSearchParams(caso.parametros).toString();
        const full=await request('/api/admin/dashboard?'+query); assert.equal(full.status,200);
        const dados=JSON.parse(full.body); assert.deepEqual(dados.indicadores,caso.indicadores);
        for(const [suffix,key] of [['indicadores','indicadores'],['vendas','vendas'],['status','situacao'],['produtos','produtos']]) {
            const r=await request('/api/admin/dashboard/'+suffix+'?'+query); assert.equal(r.status,200); assert.deepEqual(JSON.parse(r.body),dados[key]);
        }
        await filtrar(caso.parametros);
        for(const [id,key] of [['dash-faturamento','faturamento'],['dash-ticket','ticketMedio'],['dash-descontos','descontos']]) {
            assert.equal(normal(await evaluate(`document.getElementById(${JSON.stringify(id)}).textContent`)),moeda(caso.indicadores[key]));
        }
        assert.equal(Number(await evaluate('document.querySelector("#dash-pedidos").textContent')),caso.indicadores.pedidos);
        assert.equal(await evaluate('window.__homologacaoSemReload'),'preservado');
    }
    await filtrar(padrao);
    const full=JSON.parse((await request('/api/admin/dashboard?'+new URLSearchParams(padrao))).body);
    assert.deepEqual(full,esperado.resumo);
    const grafico=await evaluate('(()=>{const charts=Object.values(Chart.instances);return {vendas:charts.find(c=>c.canvas.id==="dash-vendas-grafico").data.datasets[0].data,status:charts.find(c=>c.canvas.id==="dash-status-grafico").data.datasets[0].data,tipos:charts.find(c=>c.canvas.id==="dash-tipos-grafico").data.datasets[0].data}})()');
    assert.deepEqual(grafico.vendas,full.vendas.map(v=>v.faturamento));
    assert.deepEqual(grafico.status,full.situacao.map(s=>s.quantidade));
    assert.deepEqual(grafico.tipos,full.tipos.map(t=>t.quantidade));
    const ranking=(await evaluate('Array.from(document.querySelectorAll("#dash-produtos tr")).map(tr=>Array.from(tr.cells).map(td=>td.textContent))')).map(row=>row.map(normal));
    assert.deepEqual(ranking,full.produtos.map(p=>[p.nome,String(p.quantidade),moeda(p.valorBruto)]).map(row=>row.map(normal)));
    const comparacao=normal(await evaluate('document.querySelector("#dash-comparacao").textContent'));
    assert.ok(comparacao.includes('253,50')); assert.ok(comparacao.includes('235,00'));

    // Latência induzida: verifica loading antes da resposta real, sem mockar dados.
    await command('Network.enable');
    await command('Network.emulateNetworkConditions',{offline:false,latency:600,downloadThroughput:-1,uploadThroughput:-1});
    await evaluate('document.querySelector("#dashboard-filtros").requestSubmit()');
    assert.equal(await evaluate('document.querySelector("#dashboard-carregando").hidden'),false);
    await esperar('!document.querySelector("#dashboard-filtros button").disabled');
    await command('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});

    for(const bad of ['inicio=invalido','inicio=2026-10-07&fim=2026-10-01','tipo=INVALIDO','status=INVALIDO']) {
        const r=await request('/api/admin/dashboard?'+bad); assert.equal(r.status,400); assert.ok(JSON.parse(r.body).erro);
    }
    await command('Emulation.setDeviceMetricsOverride',{width:1440,height:2200,deviceScaleFactor:1,mobile:false});
    await evaluate('void jQuery("[data-widget=pushmenu]").PushMenu("expand")'); await pausa(600);
    const ponto=await evaluate('(()=>{const c=Object.values(Chart.instances).find(c=>c.canvas.id==="dash-vendas-grafico");const p=c.getDatasetMeta(0).data[0]._model;const r=c.canvas.getBoundingClientRect();return {x:r.left+p.x,y:r.top+p.y};})()');
    await command('Input.dispatchMouseEvent',{type:'mouseMoved',...ponto});
    await esperar('Object.values(Chart.instances).find(c=>c.canvas.id==="dash-vendas-grafico").tooltip._model.opacity > 0');
    const tooltip=await evaluate('Object.values(Chart.instances).find(c=>c.canvas.id==="dash-vendas-grafico").tooltip._model.body.flatMap(b=>b.lines).join(" ")');
    assert.ok(tooltip.includes('80,00'),'Tooltip não corresponde ao primeiro dia (80,00)');
    await command('Input.dispatchMouseEvent',{type:'mouseMoved',x:1000,y:200});
    assert.ok(await evaluate('document.querySelector(".content-wrapper").getBoundingClientRect().left >= document.querySelector(".main-sidebar").getBoundingClientRect().right - 1'));
    const desktop=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});
    fs.writeFileSync('target/adminlte-desktop-demonstracao.png',Buffer.from(desktop.data,'base64'));
    await command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true}); await pausa(600);
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'));
    assert.ok(await evaluate('document.querySelector("#aviso-demonstracao").textContent.includes("DADOS DE TESTE")'));
    const mobile=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
    fs.writeFileSync('target/adminlte-mobile-demonstracao.png',Buffer.from(mobile.data,'base64'));
    console.log('OK DEMONSTRAÇÃO: 9 registros, 488.50 de faturamento, ticket 69.79, descontos 6.50; nove filtros × cinco endpoints; cards, séries, distribuições, ranking, desempate, comparação e tooltip conferidos.');
    await command('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
    await filtrar({inicio:'2026-09-01',fim:'2026-09-07',status:'',tipo:''});
    return {identificar,filtrar,padrao};
};
