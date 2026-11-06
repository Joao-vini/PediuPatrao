(() => {
    'use strict';
    const form = document.getElementById('dashboard-filtros');
    if (!form) return;
    const mensagem = document.getElementById('dashboard-mensagem');
    const resultados = document.getElementById('dashboard-resultados');
    const carregando = document.getElementById('dashboard-carregando');
    const moeda = valor => Number(valor).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
    const data = valor => valor.split('-').reverse().join('/');
    const texto = (id, valor) => { document.getElementById(id).textContent = valor; };
    const nomes = {RECEBIDO:'Recebido',EM_PREPARO:'Em preparo',EM_PREPARACAO:'Em preparação',PRONTO:'Pronto',PRONTO_PARA_RETIRADA:'Pronto para retirada',SAIU_PARA_ENTREGA:'Saiu para entrega',RETIRADO:'Retirado',FINALIZADO:'Finalizado',CANCELADO:'Cancelado',SEM_STATUS:'Sem status',ENTREGA:'Entrega',RETIRADA:'Retirada',BALCAO:'Balcão',SEM_TIPO:'Sem tipo registrado'};
    const cores = ['#278457','#527bb3','#dc9e37','#9c6cc4','#c96062','#56a4a0','#8c735e','#727b86','#a9bd6b','#7895a1'];
    const graficos = new Map();
    const hoje = new Intl.DateTimeFormat('sv-SE', {timeZone:'America/Sao_Paulo'}).format(new Date());
    form.elements.inicio.value = hoje.slice(0, 8) + '01'; form.elements.fim.value = hoje;
    let ocupado = false;
    function linha(tbody, valores) {
        const tr = document.createElement('tr');
        valores.forEach(valor => { const td = document.createElement('td'); td.textContent = valor; tr.append(td); });
        tbody.append(tr);
    }
    function grafico(id, config) {
        if (graficos.has(id)) graficos.get(id).destroy();
        graficos.set(id, new Chart(document.getElementById(id).getContext('2d'), config));
    }
    function distribuicao(itens, campo, resumoId, graficoId) {
        const resumo = document.getElementById(resumoId); resumo.replaceChildren();
        document.getElementById(graficoId).parentElement.hidden = !itens.length;
        itens.forEach(item => {
            const row = document.createElement('div'); row.className = 'dash-status-row';
            const nome = document.createElement('span'); nome.textContent = nomes[item[campo]] || item[campo];
            const quantidade = document.createElement('strong'); quantidade.textContent = item.quantidade;
            row.append(nome, quantidade); resumo.append(row);
        });
        if (!itens.length) resumo.textContent = 'Nenhum pedido para distribuir.';
        grafico(graficoId, {type:'doughnut', data:{labels:itens.map(i => nomes[i[campo]] || i[campo]),
            datasets:[{data:itens.map(i => i.quantidade), backgroundColor:cores, borderWidth:1}]},
            options:{responsive:true, maintainAspectRatio:false, legend:{display:false},
                tooltips:{callbacks:{label:(item, chart) => `${chart.labels[item.index]}: ${chart.datasets[0].data[item.index]} pedidos`}}}});
    }
    function comparar(dias) {
        if (dias.length < 2) { texto('dash-comparacao', 'Selecione pelo menos dois dias para comparar as partes do período.'); return; }
        const meio = Math.ceil(dias.length / 2);
        const primeiro = dias.slice(0, meio), segundo = dias.slice(meio);
        const soma = parte => parte.reduce((total, dia) => total + Math.round(Number(dia.faturamento) * 100), 0) / 100;
        texto('dash-comparacao', `Comparação dentro do período: ${data(primeiro[0].data)} a ${data(primeiro[primeiro.length - 1].data)} (${primeiro.length} dias): ${moeda(soma(primeiro))}; ${data(segundo[0].data)} a ${data(segundo[segundo.length - 1].data)} (${segundo.length} dias): ${moeda(soma(segundo))}. Totais das partes, sem ajuste pela quantidade de dias.`);
    }
    async function atualizar(event) {
        if (event) event.preventDefault();
        if (ocupado) return;
        ocupado = true; resultados.hidden = true; carregando.hidden = false;
        mensagem.className = ''; mensagem.textContent = 'Carregando os pedidos…';
        form.querySelector('button').disabled = true; resultados.setAttribute('aria-busy', 'true');
        try {
            const endpoint = new URL('api/admin/dashboard', new URL('.', document.baseURI));
            endpoint.search = new URLSearchParams(new FormData(form)).toString();
            const response = await fetch(endpoint, {headers: {Accept:'application/json'}, credentials:'same-origin', signal:AbortSignal.timeout(30000), cache:'no-store'});
            if (response.redirected || response.status === 401) throw new Error('Sessão encerrada. Faça login novamente.');
            if (!response.ok) throw new Error(response.status === 400 ? 'Confira as datas (máximo de 366 dias), o status e o tipo.' : response.status === 422 ? 'Mais de 50.000 pedidos. Reduza o período ou aplique filtros.' : response.status === 403 ? 'Acesso exclusivo do administrador.' : 'Não foi possível consultar os pedidos. Tente atualizar novamente.');
            const dados = await response.json(); const i = dados.indicadores;
            texto('dash-faturamento', moeda(i.faturamento)); texto('dash-pedidos', i.pedidos);
            texto('dash-ticket', moeda(i.ticketMedio)); texto('dash-descontos', moeda(i.descontos));
            texto('dash-cancelados', `${i.cancelados} cancelados · ${i.semValor} sem valor válido`);
            const dias = document.getElementById('dash-dias'); dias.replaceChildren();
            dados.vendas.forEach(d => linha(dias, [data(d.data), d.pedidos, moeda(d.faturamento)]));
            comparar(dados.vendas);
            const produtos = document.getElementById('dash-produtos'); produtos.replaceChildren();
            dados.produtos.forEach(p => linha(produtos, [p.nome, p.quantidade, moeda(p.valorBruto)]));
            if (!dados.produtos.length) linha(produtos, ['Nenhum item elegível neste período.', '—', '—']);
            if (typeof Chart === 'undefined') throw new Error('Não foi possível carregar a biblioteca de gráficos. Recarregue a página.');
            resultados.hidden = false;
            grafico('dash-vendas-grafico', {type:'line', data:{labels:dados.vendas.map(d => data(d.data)),
                datasets:[{label:'Faturamento diário',data:dados.vendas.map(d => Number(d.faturamento)),backgroundColor:'rgba(39,132,87,.12)',borderColor:'#278457',borderWidth:2,pointRadius:3,lineTension:0}]},
                options:{responsive:true,maintainAspectRatio:false,legend:{labels:{usePointStyle:true}},
                    tooltips:{mode:'index',intersect:false,callbacks:{label:item => moeda(item.yLabel)}},
                    scales:{yAxes:[{ticks:{beginAtZero:true,callback:valor => moeda(valor)}}],xAxes:[{ticks:{maxTicksLimit:12}}]}}});
            distribuicao(dados.situacao, 'status', 'dash-status', 'dash-status-grafico');
            distribuicao(dados.tipos, 'tipo', 'dash-tipos', 'dash-tipos-grafico');
            mensagem.textContent = i.pedidos ? `Dados atualizados. ${i.semValor ? 'Há pedidos sem valor válido, excluídos das métricas financeiras.' : ''}` : 'Nenhum pedido encontrado para estes filtros.';
        } catch (erro) {
            resultados.hidden = true; mensagem.className = 'dash-error';
            mensagem.textContent = erro.name === 'TimeoutError' ? 'A consulta demorou demais. Reduza o período e tente novamente.' : erro instanceof TypeError ? 'Falha de conexão. Tente atualizar novamente.' : erro.message;
        } finally { ocupado = false; carregando.hidden = true; form.querySelector('button').disabled = false; resultados.setAttribute('aria-busy','false'); }
    }
    form.addEventListener('submit', atualizar); atualizar();
})();
