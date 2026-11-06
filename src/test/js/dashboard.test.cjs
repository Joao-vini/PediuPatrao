const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const source = readFileSync('src/main/resources/static/js/dashboard.js', 'utf8');

function ambiente(fetch, comChart = true) {
    class Elemento {
        constructor() { this.children = []; this.style = {}; this.textContent = ''; this.hidden = false; this.parentElement = {}; }
        append(...children) { this.children.push(...children); }
        replaceChildren() { this.children = []; }
        setAttribute() {}
        addEventListener(evento, callback) { this[evento] = callback; }
        querySelector() { return this.button; }
        getContext() { return this; }
    }
    const ids = new Map();
    const get = id => { if (!ids.has(id)) ids.set(id, new Elemento()); return ids.get(id); };
    const form = get('dashboard-filtros');
    form.elements = {inicio: {}, fim: {}}; form.button = new Elemento();
    const document = {baseURI:'http://localhost:8080/app/dashboard', getElementById:get, createElement:() => new Elemento()};
    class FormData { constructor() { return [['inicio','2026-10-01'], ['fim','2026-10-02']]; } }
    const graficos = [];
    class Chart {
        constructor(context, config) { this.config = config; this.destruido = false; graficos.push(this); }
        destroy() { this.destruido = true; }
    }
    vm.runInNewContext(source, {document, fetch, URL, URLSearchParams, FormData, Intl, Date, AbortSignal, Chart:comChart ? Chart : undefined});
    return {get, form, graficos};
}
const vazio = {indicadores:{pedidos:0,cancelados:0,semValor:0,faturamento:0,ticketMedio:0,descontos:0}, vendas:[], situacao:[], produtos:[], tipos:[]};
const terminar = () => new Promise(resolve => setImmediate(resolve));

test('consulta API no contexto correto e mostra ausência de dados', async () => {
    let url;
    const {get, form} = ambiente(async endpoint => { url = endpoint; return {ok:true,json:async () => vazio}; });
    await terminar();
    assert.equal(url.pathname, '/app/api/admin/dashboard');
    assert.equal(url.searchParams.get('inicio'), '2026-10-01');
    assert.match(get('dashboard-mensagem').textContent, /Nenhum pedido/);
    assert.equal(get('dashboard-resultados').hidden, false);
    assert.equal(form.button.disabled, false);
});
test('erro não mantém indicadores antigos visíveis e permite tentar novamente', async () => {
    const {get, form} = ambiente(async () => ({ok:false,status:503}));
    await terminar();
    assert.match(get('dashboard-mensagem').textContent, /Não foi possível/);
    assert.equal(get('dashboard-resultados').hidden, true);
    assert.equal(form.button.disabled, false);
});
test('nomes são texto, quantidades e gráfico vêm da resposta real', async () => {
    const dados = {...vazio, indicadores:{...vazio.indicadores,pedidos:1,faturamento:80},
        vendas:[{data:'2026-10-01',pedidos:1,faturamento:80}], situacao:[{status:'RECEBIDO',quantidade:1}],
        produtos:[{nome:'<img src=x onerror=alert(1)>',quantidade:2,valorBruto:100}]};
    const {get, graficos} = ambiente(async () => ({ok:true,json:async () => dados}));
    await terminar();
    assert.equal(get('dash-produtos').children[0].children[0].textContent, dados.produtos[0].nome);
    assert.equal(graficos.length, 3);
    assert.equal(graficos[0].config.type, 'line');
    assert.equal(graficos[0].config.data.datasets[0].data[0], 80);
    assert.equal(graficos[1].config.data.datasets[0].data[0], 1);
});
test('redirecionamento de login é apresentado como sessão encerrada', async () => {
    const {get} = ambiente(async () => ({redirected:true,status:200}));
    await terminar(); assert.match(get('dashboard-mensagem').textContent, /Sessão encerrada/);
});
test('atualizar destrói gráficos anteriores e usa nova resposta', async () => {
    const {form, graficos, get} = ambiente(async () => ({ok:true,json:async () => vazio}));
    await terminar();
    await form.submit({preventDefault(){}});
    assert.equal(graficos.length, 6);
    assert.ok(graficos.slice(0,3).every(g => g.destruido));
    assert.equal(get('dashboard-carregando').hidden, true);
});
test('compara partes do período deixando a quantidade de dias explícita', async () => {
    const dados = {...vazio,vendas:[{data:'2026-10-01',faturamento:10,pedidos:1},{data:'2026-10-02',faturamento:20,pedidos:1},{data:'2026-10-03',faturamento:40,pedidos:1}]};
    const {get} = ambiente(async () => ({ok:true,json:async () => dados}));
    await terminar();
    assert.match(get('dash-comparacao').textContent, /2 dias/);
    assert.match(get('dash-comparacao').textContent, /30,00/);
    assert.match(get('dash-comparacao').textContent, /40,00/);
});
test('falha ao carregar Chart.js é explícita e não mostra sucesso', async () => {
    const {get} = ambiente(async () => ({ok:true,json:async () => vazio}), false);
    await terminar();
    assert.match(get('dashboard-mensagem').textContent, /biblioteca de gráficos/);
    assert.equal(get('dashboard-resultados').hidden, true);
});
